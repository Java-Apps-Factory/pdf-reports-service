package com.example.reporting.api.dto;

/** Result of {@code POST /report-runs/{runId}/retransmit-failed}. */
public final class RetransmitRunResponse {

    private final long runId;
    private final int requeued;

    /**
     * Creates the response.
     *
     * @param runId run ID
     * @param requeued documents re-queued for sending
     */
    public RetransmitRunResponse(long runId, int requeued) {
        this.runId = runId;
        this.requeued = requeued;
    }

    public long getRunId() {
        return runId;
    }

    public int getRequeued() {
        return requeued;
    }
}
