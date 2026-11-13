package com.example.reporting.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;

/** A run as returned by the API; {@code documentsByStatus} only in the detail view. */
public final class RunResponse {

    private final long runId;
    private final String stateCode;
    private final String status;
    private final String triggerType;
    private final String requestedBy;
    private final OffsetDateTime dataAsOf;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime completedAt;
    private final int totalDocuments;
    private final Map<String, Long> documentsByStatus;

    /**
     * Creates the response.
     *
     * @param runId run ID
     * @param stateCode state code
     * @param status run status
     * @param triggerType trigger
     * @param requestedBy requester
     * @param dataAsOf data-as-of time
     * @param createdAt creation time
     * @param completedAt completion time, may be {@code null}
     * @param totalDocuments documents created
     * @param documentsByStatus counts per status, or {@code null} to omit
     */
    public RunResponse(long runId, String stateCode, String status, String triggerType, String requestedBy,
            OffsetDateTime dataAsOf, OffsetDateTime createdAt, OffsetDateTime completedAt, int totalDocuments,
            Map<String, Long> documentsByStatus) {
        this.runId = runId;
        this.stateCode = stateCode;
        this.status = status;
        this.triggerType = triggerType;
        this.requestedBy = requestedBy;
        this.dataAsOf = dataAsOf;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.totalDocuments = totalDocuments;
        this.documentsByStatus = documentsByStatus == null ? null : Map.copyOf(documentsByStatus);
    }

    public long getRunId() {
        return runId;
    }

    public String getStateCode() {
        return stateCode;
    }

    public String getStatus() {
        return status;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public OffsetDateTime getDataAsOf() {
        return dataAsOf;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public int getTotalDocuments() {
        return totalDocuments;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Long> getDocumentsByStatus() {
        return documentsByStatus;
    }
}
