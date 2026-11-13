package com.example.reporting.transmission;

/**
 * Published inside the transaction that marks a document GENERATED (Observer). Phase 4's send poller listens
 * after commit, so delivery starts without waiting for the next poll.
 */
public final class DocumentGeneratedEvent {

    private final long docId;
    private final long runId;
    private final String stateCode;

    /**
     * Creates the event.
     *
     * @param docId document ID
     * @param runId run ID
     * @param stateCode state code
     */
    public DocumentGeneratedEvent(long docId, long runId, String stateCode) {
        this.docId = docId;
        this.runId = runId;
        this.stateCode = stateCode;
    }

    public long docId() {
        return docId;
    }

    public long runId() {
        return runId;
    }

    public String stateCode() {
        return stateCode;
    }
}
