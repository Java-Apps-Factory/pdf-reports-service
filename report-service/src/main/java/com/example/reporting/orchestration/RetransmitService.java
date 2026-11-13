package com.example.reporting.orchestration;

import com.example.reporting.orchestration.RetransmitRefusedException.Reason;
import com.example.reporting.tracking.DbClock;
import com.example.reporting.tracking.DocumentClaimRepository;
import com.example.reporting.tracking.DocumentStatus;
import com.example.reporting.tracking.ReportDocument;
import com.example.reporting.tracking.ReportDocumentRepository;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.ReportRunRepository;
import com.example.reporting.tracking.RunStatus;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual retransmission (FR-16, SPEC section 9): SEND_FAILED or SEND_FAILED_FINAL documents become
 * SEND_FAILED, due now, with attempts reset. The stored PDF is resent as-is (FR-14). A run that was already
 * completed goes back to DISPATCHING so the finalizer completes it again.
 */
@Service
public class RetransmitService {

    private static final Logger LOG = LoggerFactory.getLogger(RetransmitService.class);

    private final ReportDocumentRepository documents;
    private final ReportRunRepository runs;
    private final DocumentClaimRepository claims;
    private final DbClock dbClock;

    /**
     * Creates the service.
     *
     * @param documents document repository
     * @param runs run repository
     * @param claims status changes
     * @param dbClock database clock
     */
    public RetransmitService(ReportDocumentRepository documents, ReportRunRepository runs,
            DocumentClaimRepository claims, DbClock dbClock) {
        this.documents = documents;
        this.runs = runs;
        this.claims = claims;
        this.dbClock = dbClock;
    }

    /**
     * Re-queues one document.
     *
     * @param docId document ID
     * @throws RetransmitRefusedException if the document is missing, purged, superseded or not failed
     */
    @Transactional
    public void retransmitDocument(long docId) {
        ReportDocument document = documents.findById(docId).orElseThrow(() -> new RetransmitRefusedException(
                Reason.NOT_FOUND, "Document " + docId + " does not exist."));
        if (document.getContentPurgedAt() != null) {
            throw new RetransmitRefusedException(Reason.GONE, "The PDF of document " + docId + " was purged.");
        }
        DocumentStatus status = document.getStatus();
        if (status != DocumentStatus.SEND_FAILED && status != DocumentStatus.SEND_FAILED_FINAL) {
            throw new RetransmitRefusedException(Reason.CONFLICT, "Document " + docId + " is " + status
                    + "; only SEND_FAILED and SEND_FAILED_FINAL documents can be retransmitted.");
        }
        Instant now = dbClock.now();
        if (!claims.requeueForSend(docId, now)) {
            throw new RetransmitRefusedException(Reason.CONFLICT, "Document " + docId + " changed meanwhile.");
        }
        reopen(document.getRunId(), now);
        LOG.info("Document {} re-queued for sending", docId);
    }

    /**
     * Re-queues every SEND_FAILED and SEND_FAILED_FINAL document of a run whose PDF is still stored.
     *
     * @param runId run ID
     * @return number of documents re-queued
     * @throws RetransmitRefusedException if the run does not exist
     */
    @Transactional
    public int retransmitFailedOfRun(long runId) {
        if (runs.findById(runId).isEmpty()) {
            throw new RetransmitRefusedException(Reason.NOT_FOUND, "Run " + runId + " does not exist.");
        }
        Instant now = dbClock.now();
        int requeued = claims.requeueFailedOfRun(runId, now);
        if (requeued > 0) {
            reopen(runId, now);
        }
        LOG.info("Run {}: {} failed documents re-queued for sending", runId, requeued);
        return requeued;
    }

    private void reopen(long runId, Instant now) {
        ReportRun run = runs.findById(runId).orElseThrow();
        if (run.getStatus() == RunStatus.COMPLETED || run.getStatus() == RunStatus.COMPLETED_WITH_ERRORS) {
            run.reopenForDispatch();
            runs.saveAndFlush(run);
        }
    }
}
