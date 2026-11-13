package com.example.reporting.orchestration;

import com.example.reporting.pdf.RenderResult;
import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.rendering.FipsReportComposer;
import com.example.reporting.rendering.ReportRequest;
import com.example.reporting.tracking.Backoff;
import com.example.reporting.tracking.DbClock;
import com.example.reporting.tracking.DocumentClaimRepository;
import com.example.reporting.tracking.DocumentClaimRepository.Claim;
import com.example.reporting.tracking.DocumentClaimRepository.GeneratedFacts;
import com.example.reporting.tracking.DocumentStatus;
import com.example.reporting.tracking.ReportContentStore;
import com.example.reporting.tracking.ReportDocument;
import com.example.reporting.tracking.ReportDocumentRepository;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.ReportRunRepository;
import com.example.reporting.transmission.DocumentGeneratedEvent;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stage B of SPEC section 8 for one claimed document: renders the PDF to a temp file (no transaction open),
 * then in one short transaction stores it in {@code REPORT_CONTENT} and marks the document GENERATED, and
 * publishes {@link DocumentGeneratedEvent}. Failures are classified once and recorded with backoff.
 */
@Component
public class GenerationWorker {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationWorker.class);
    private static final double LEASE_WARNING_SHARE = 0.8;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final ReportDocumentRepository documents;
    private final ReportRunRepository runs;
    private final FipsReportComposer composer;
    private final ReportContentStore content;
    private final DocumentClaimRepository claims;
    private final DbClock dbClock;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final Path tempDir;
    private final Duration lease;
    private final int maxAttempts;
    private final Backoff backoff;
    private final String nodeId;

    /**
     * Creates the worker.
     *
     * @param documents document repository
     * @param runs run repository
     * @param composer renders the PDF
     * @param content PDF store
     * @param claims status changes
     * @param dbClock database clock
     * @param transactionManager transaction manager
     * @param events event publisher
     * @param properties reporting configuration
     */
    public GenerationWorker(ReportDocumentRepository documents, ReportRunRepository runs,
            FipsReportComposer composer, ReportContentStore content, DocumentClaimRepository claims, DbClock dbClock,
            PlatformTransactionManager transactionManager, ApplicationEventPublisher events,
            ReportingProperties properties) {
        this.documents = documents;
        this.runs = runs;
        this.composer = composer;
        this.content = content;
        this.claims = claims;
        this.dbClock = dbClock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.events = events;
        ReportingProperties.Generation generation = properties.getGeneration();
        this.tempDir = generation.getTempDir();
        this.lease = generation.getLeaseDuration();
        this.maxAttempts = generation.getMaxAttempts();
        this.backoff = new Backoff(generation.getBackoff().getInitial(), generation.getBackoff().getMultiplier(),
                generation.getBackoff().getMax());
        this.nodeId = properties.getNodeId();
    }

    /**
     * Generates one claimed document. Never throws: every failure is recorded on the document, and a failure to
     * record it is left to lease recovery.
     *
     * @param claim the claim this node won
     */
    public void generate(Claim claim) {
        long started = System.nanoTime();
        Path file = tempDir.resolve("doc-" + claim.docId() + "-" + UUID.randomUUID() + ".pdf");
        ReportDocument document = null;
        try {
            MDC.put("nodeId", nodeId);
            MDC.put("docId", String.valueOf(claim.docId()));
            document = documents.findById(claim.docId())
                    .orElseThrow(() -> new IllegalStateException("Claimed document disappeared"));
            ReportRun run = runs.findById(document.getRunId())
                    .orElseThrow(() -> new IllegalStateException("Run of document is missing"));
            MDC.put("runId", String.valueOf(run.getRunId()));
            MDC.put("stateCode", document.getStateCode());
            MDC.put("fipsCode", document.getFipsCode());
            render(claim, document, run, file);
            warnIfSlow(started);
        } catch (RuntimeException | IOException e) {
            recordFailure(claim, document, e);
        } finally {
            deleteQuietly(file);
            MDC.clear();
        }
    }

    private void render(Claim claim, ReportDocument document, ReportRun run, Path file) throws IOException {
        Files.createDirectories(tempDir);
        MessageDigest digest = sha256();
        RenderResult result;
        try (OutputStream out = new DigestOutputStream(new BufferedOutputStream(Files.newOutputStream(file)),
                digest)) {
            result = composer.compose(ReportRequest.builder()
                    .stateCode(document.getStateCode())
                    .fipsCode(document.getFipsCode())
                    .runId(run.getRunId())
                    .docId(document.getDocId())
                    .dataAsOf(run.getDataAsOf())
                    .build(), out);
        }
        GeneratedFacts facts = new GeneratedFacts(result.rowsWritten(), result.pageCount(), Files.size(file),
                hex(digest.digest()));
        Boolean stored = transaction.execute(status -> {
            content.store(document.getDocId(), file);
            if (!claims.markGenerated(claim, facts, dbClock.now())) {
                status.setRollbackOnly();
                return false;
            }
            events.publishEvent(new DocumentGeneratedEvent(document.getDocId(), run.getRunId(),
                    document.getStateCode()));
            return true;
        });
        if (Boolean.TRUE.equals(stored)) {
            LOG.info("Generated document {}: {} records, {} pages, {} bytes", document.getDocId(),
                    facts.recordCount(), facts.pageCount(), facts.sizeBytes());
        } else {
            LOG.warn("Lease on document {} was lost before it could be stored; result discarded",
                    document.getDocId());
        }
    }

    private void recordFailure(Claim claim, ReportDocument document, Exception failure) {
        GenerationFailureClassifier.Outcome outcome = GenerationFailureClassifier.classify(failure);
        int attempts = document == null ? maxAttempts : document.getGenerationAttempts();
        DocumentStatus target;
        Instant nextAttempt = null;
        try {
            Instant now = dbClock.now();
            if (outcome == GenerationFailureClassifier.Outcome.DATA_ERROR) {
                target = DocumentStatus.DATA_ERROR;
                LOG.error("Document {} has a data error: {}", claim.docId(), failure.getMessage());
            } else if (outcome == GenerationFailureClassifier.Outcome.RETRY && attempts < maxAttempts) {
                target = DocumentStatus.GENERATION_FAILED;
                nextAttempt = now.plus(backoff.delay(attempts));
                LOG.warn("Generation of document {} failed (attempt {} of {}), retry at {}", claim.docId(), attempts,
                        maxAttempts, nextAttempt, failure);
            } else {
                target = DocumentStatus.GENERATION_FAILED_FINAL;
                LOG.error("Generation of document {} failed for good after attempt {}", claim.docId(), attempts,
                        failure);
            }
            if (!claims.markGenerationFailed(claim, target, nextAttempt,
                    GenerationFailureClassifier.describe(failure), now)) {
                LOG.warn("Lease on document {} was lost; failure not recorded", claim.docId());
            }
        } catch (RuntimeException e) {
            LOG.error("Could not record the failure of document {}; lease recovery will retry", claim.docId(), e);
        }
    }

    private void warnIfSlow(long startedNanos) {
        Duration took = Duration.ofNanos(System.nanoTime() - startedNanos);
        if (took.toMillis() > lease.toMillis() * LEASE_WARNING_SHARE) {
            LOG.warn("Generation took {} s, more than 80% of the {} s lease", took.toSeconds(), lease.toSeconds());
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.warn("Could not delete temp file {}", file.getFileName());
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        return hex.toString();
    }
}
