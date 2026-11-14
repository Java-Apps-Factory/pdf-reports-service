package com.example.reporting.transmission;

import com.example.reporting.PermanentTransmissionException;
import com.example.reporting.observability.ReportingMetrics;
import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.tracking.Backoff;
import com.example.reporting.tracking.DbClock;
import com.example.reporting.tracking.DocumentClaimRepository;
import com.example.reporting.tracking.DocumentClaimRepository.Claim;
import com.example.reporting.tracking.DocumentStatus;
import com.example.reporting.tracking.ReportContentStore;
import com.example.reporting.tracking.ReportDocument;
import com.example.reporting.tracking.ReportDocumentRepository;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.ReportRunRepository;
import com.example.reporting.tracking.Sha256;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage C of SPEC section 8 for one claimed document: supersede it if a newer document exists (FR-15), load
 * and verify the stored PDF, send it with in-process retries for transient errors, and record SENT with the
 * JMS message ID. A resend never regenerates the PDF (FR-14). Failures are classified once and recorded with
 * backoff.
 */
@Component
public class SendWorker {

    private static final Logger LOG = LoggerFactory.getLogger(SendWorker.class);

    private final ReportDocumentRepository documents;
    private final ReportRunRepository runs;
    private final ReportContentStore content;
    private final DocumentClaimRepository claims;
    private final ReportTransmitter transmitter;
    private final DbClock dbClock;
    private final RetryTemplate retry;
    private final Backoff backoff;
    private final int maxAttempts;
    private final ZoneId zone;
    private final String nodeId;
    private final ReportingMetrics metrics;

    /**
     * Creates the worker.
     *
     * @param documents document repository
     * @param runs run repository
     * @param content PDF store
     * @param claims status changes
     * @param transmitter message sender
     * @param dbClock database clock
     * @param properties reporting configuration
     * @param metrics meters
     */
    public SendWorker(ReportDocumentRepository documents, ReportRunRepository runs, ReportContentStore content,
            DocumentClaimRepository claims, ReportTransmitter transmitter, DbClock dbClock,
            ReportingProperties properties, ReportingMetrics metrics) {
        this.documents = documents;
        this.runs = runs;
        this.content = content;
        this.claims = claims;
        this.transmitter = transmitter;
        this.dbClock = dbClock;
        ReportingProperties.Transmission settings = properties.getTransmission();
        this.retry = retryTemplate(settings.getInProcess());
        this.backoff = new Backoff(settings.getBackoff().getInitial(), settings.getBackoff().getMultiplier(),
                settings.getBackoff().getMax());
        this.maxAttempts = settings.getMaxAttempts();
        this.zone = properties.getBusinessZone();
        this.nodeId = properties.getNodeId();
        this.metrics = metrics;
    }

    /**
     * Sends one claimed document. Never throws: every outcome is recorded on the document, and a failure to
     * record it is left to lease recovery.
     *
     * @param claim the claim this node won
     */
    public void send(Claim claim) {
        long started = System.nanoTime();
        ReportDocument document = null;
        try {
            MDC.put("nodeId", nodeId);
            MDC.put("docId", String.valueOf(claim.docId()));
            document = documents.findById(claim.docId())
                    .orElseThrow(() -> new IllegalStateException("Claimed document disappeared"));
            MDC.put("runId", String.valueOf(document.getRunId()));
            MDC.put("stateCode", document.getStateCode());
            MDC.put("fipsCode", document.getFipsCode());
            if (claims.newerDocumentExists(claim.docId())) {
                supersede(claim);
                metrics.sendFinished(document.getStateCode(), "superseded", since(started));
                return;
            }
            ReportMessage message = message(document);
            String messageId = retry.execute((RetryCallback<String, RuntimeException>) context ->
                    transmitter.send(message));
            metrics.sendFinished(document.getStateCode(), "sent", since(started));
            if (claims.markSent(claim, messageId, dbClock.now())) {
                LOG.info("Sent document {} ({} bytes), JMSMessageID={}", claim.docId(), message.size(), messageId);
            } else {
                LOG.warn("Document {} was sent but its lease was lost; a duplicate send is possible",
                        claim.docId());
            }
        } catch (RuntimeException e) {
            String state = document == null ? "unknown" : document.getStateCode();
            metrics.sendFinished(state, "failed", since(started));
            metrics.sendFailed(state, SendFailureClassifier.isPermanent(e));
            recordFailure(claim, document, e);
        } finally {
            MDC.clear();
        }
    }

    private void supersede(Claim claim) {
        if (claims.markSendEnded(claim, DocumentStatus.SUPERSEDED, null,
                "A newer document for this FIPS code exists", dbClock.now())) {
            LOG.info("Document {} superseded by a newer document; not sent", claim.docId());
        }
    }

    private ReportMessage message(ReportDocument document) {
        if (document.getContentPurgedAt() != null) {
            throw new PermanentTransmissionException("Stored PDF was purged", null);
        }
        byte[] pdf = content.load(document.getDocId())
                .orElseThrow(() -> new PermanentTransmissionException("Stored PDF is missing", null));
        String expected = document.getSha256() == null ? "" : document.getSha256().trim();
        if (!Sha256.hex(pdf).equals(expected)) {
            throw new PermanentTransmissionException("Stored PDF does not match its SHA-256", null);
        }
        ReportRun run = runs.findById(document.getRunId())
                .orElseThrow(() -> new PermanentTransmissionException("Run of document is missing", null));
        return ReportMessage.builder()
                .identity(document.getDocId(), run.getRunId(), document.getStateCode(), document.getFipsCode())
                .times(iso(document.getGeneratedAt()), iso(run.getDataAsOf()))
                .content(document.getRecordCount() == null ? 0 : document.getRecordCount(),
                        document.getPageCount() == null ? 0 : document.getPageCount(), expected, pdf)
                .build();
    }

    private void recordFailure(Claim claim, ReportDocument document, RuntimeException failure) {
        boolean permanent = SendFailureClassifier.isPermanent(failure);
        int attempts = document == null ? maxAttempts : document.getSendAttempts();
        try {
            Instant now = dbClock.now();
            DocumentStatus target;
            Instant nextAttempt = null;
            if (!permanent && attempts < maxAttempts) {
                target = DocumentStatus.SEND_FAILED;
                nextAttempt = now.plus(backoff.delay(attempts));
                LOG.warn("Send of document {} failed (attempt {} of {}), retry at {}: {}", claim.docId(), attempts,
                        maxAttempts, nextAttempt, SendFailureClassifier.describe(failure));
            } else {
                target = DocumentStatus.SEND_FAILED_FINAL;
                LOG.error("Send of document {} failed for good after attempt {}", claim.docId(), attempts, failure);
            }
            if (!claims.markSendEnded(claim, target, nextAttempt, SendFailureClassifier.describe(failure), now)) {
                LOG.warn("Lease on document {} was lost; failure not recorded", claim.docId());
            }
        } catch (RuntimeException e) {
            LOG.error("Could not record the send failure of document {}; lease recovery will retry", claim.docId(),
                    e);
        }
    }

    private static Duration since(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }

    private String iso(Instant instant) {
        return instant == null ? "" : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                instant.atZone(zone).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS));
    }

    private static RetryTemplate retryTemplate(ReportingProperties.InProcessRetry settings) {
        ExponentialBackOffPolicy delays = new ExponentialBackOffPolicy();
        delays.setInitialInterval(settings.getInitial().toMillis());
        delays.setMultiplier(settings.getMultiplier());
        delays.setMaxInterval(settings.getMax().toMillis());
        RetryPolicy transientPolicy = new SimpleRetryPolicy(settings.getAttempts());
        RetryPolicy never = new NeverRetryPolicy();
        ExceptionClassifierRetryPolicy policy = new ExceptionClassifierRetryPolicy();
        policy.setExceptionClassifier(failure -> SendFailureClassifier.isPermanent(failure) ? never : transientPolicy);
        RetryTemplate template = new RetryTemplate();
        template.setBackOffPolicy(delays);
        template.setRetryPolicy(policy);
        return template;
    }

}
