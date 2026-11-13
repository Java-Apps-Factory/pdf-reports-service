package com.example.reporting.tracking;

import java.time.Instant;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/** One report run of one state ({@code REPORT_RUN}); optimistic locking on {@code VERSION}. */
@Entity
@Table(name = "REPORT_RUN")
public class ReportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RUN_ID")
    private Long runId;

    @Column(name = "STATE_CODE", nullable = false, columnDefinition = "character(2)")
    private String stateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRIGGER_TYPE", nullable = false, length = 10)
    private TriggerType triggerType;

    @Column(name = "REQUESTED_BY", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "FIPS_FILTER", length = 2000)
    private String fipsFilter;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private RunStatus status;

    @Column(name = "DATA_AS_OF", nullable = false)
    private Instant dataAsOf;

    @Column(name = "TOTAL_DOCUMENTS", nullable = false)
    private int totalDocuments;

    @Column(name = "FAILURE_REASON", length = 1000)
    private String failureReason;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "COMPLETED_AT")
    private Instant completedAt;

    @Version
    @Column(name = "VERSION", nullable = false)
    private int version;

    /** For JPA only. */
    protected ReportRun() {
    }

    /**
     * Creates a new RUNNING run.
     *
     * @param stateCode state code
     * @param triggerType trigger
     * @param requestedBy caller identity
     * @param fipsFilter comma-separated FIPS filter, or {@code null} for all
     * @param now database time; becomes the data-as-of and creation time
     * @return the unsaved run
     */
    public static ReportRun start(String stateCode, TriggerType triggerType, String requestedBy, String fipsFilter,
            Instant now) {
        ReportRun run = new ReportRun();
        run.stateCode = Objects.requireNonNull(stateCode, "stateCode");
        run.triggerType = Objects.requireNonNull(triggerType, "triggerType");
        run.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        run.fipsFilter = fipsFilter;
        run.status = RunStatus.RUNNING;
        run.dataAsOf = Objects.requireNonNull(now, "now");
        run.createdAt = now;
        return run;
    }

    /**
     * Records the number of documents created.
     *
     * @param count documents
     */
    public void setTotalDocuments(int count) {
        this.totalDocuments = count;
    }

    /**
     * Moves the run to a new status.
     *
     * @param newStatus the status
     * @param now database time; sets {@code COMPLETED_AT} for completed statuses
     */
    public void moveTo(RunStatus newStatus, Instant now) {
        this.status = Objects.requireNonNull(newStatus, "newStatus");
        if (newStatus == RunStatus.COMPLETED || newStatus == RunStatus.COMPLETED_WITH_ERRORS
                || newStatus == RunStatus.FAILED) {
            this.completedAt = now;
        }
    }

    /**
     * Moves a completed run back to DISPATCHING after a manual retransmit, so the finalizer completes it again.
     */
    public void reopenForDispatch() {
        this.status = RunStatus.DISPATCHING;
        this.completedAt = null;
    }

    public Long getRunId() {
        return runId;
    }

    public String getStateCode() {
        return stateCode == null ? null : stateCode.trim();
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getFipsFilter() {
        return fipsFilter;
    }

    public RunStatus getStatus() {
        return status;
    }

    public Instant getDataAsOf() {
        return dataAsOf;
    }

    public int getTotalDocuments() {
        return totalDocuments;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public int getVersion() {
        return version;
    }
}
