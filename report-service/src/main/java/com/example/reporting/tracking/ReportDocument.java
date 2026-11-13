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

/**
 * One PDF of one run ({@code REPORT_DOCUMENT}). JPA creates and reads documents; every status change after
 * creation goes through {@link DocumentClaimRepository}'s conditional UPDATEs (SPEC section 9).
 */
@Entity
@Table(name = "REPORT_DOCUMENT")
public class ReportDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DOC_ID")
    private Long docId;

    @Column(name = "RUN_ID", nullable = false)
    private Long runId;

    @Column(name = "STATE_CODE", nullable = false, columnDefinition = "character(2)")
    private String stateCode;

    @Column(name = "FIPS_CODE", nullable = false, columnDefinition = "character(5)")
    private String fipsCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private DocumentStatus status;

    @Column(name = "RECORD_COUNT")
    private Integer recordCount;

    @Column(name = "PAGE_COUNT")
    private Integer pageCount;

    @Column(name = "SIZE_BYTES")
    private Long sizeBytes;

    @Column(name = "SHA256", columnDefinition = "character(64)")
    private String sha256;

    @Column(name = "GENERATION_ATTEMPTS", nullable = false)
    private short generationAttempts;

    @Column(name = "SEND_ATTEMPTS", nullable = false)
    private short sendAttempts;

    @Column(name = "NEXT_ATTEMPT_AT")
    private Instant nextAttemptAt;

    @Column(name = "LEASE_OWNER", length = 100)
    private String leaseOwner;

    @Column(name = "LEASE_EXPIRES_AT")
    private Instant leaseExpiresAt;

    @Column(name = "LAST_ERROR", length = 2000)
    private String lastError;

    @Column(name = "JMS_MESSAGE_ID", length = 100)
    private String jmsMessageId;

    @Column(name = "GENERATED_AT")
    private Instant generatedAt;

    @Column(name = "SENT_AT")
    private Instant sentAt;

    @Column(name = "CONTENT_PURGED_AT")
    private Instant contentPurgedAt;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "VERSION", nullable = false)
    private int version;

    /** For JPA only. */
    protected ReportDocument() {
    }

    /**
     * Creates a new document in an initial status.
     *
     * @param runId owning run
     * @param stateCode state code
     * @param fipsCode FIPS code
     * @param status PENDING or DATA_ERROR
     * @param lastError error for DATA_ERROR, otherwise {@code null}
     * @param now database time
     * @return the unsaved document
     */
    public static ReportDocument create(long runId, String stateCode, String fipsCode, DocumentStatus status,
            String lastError, Instant now) {
        if (!status.isInitial()) {
            throw new IllegalArgumentException("A new document cannot start as " + status);
        }
        ReportDocument document = new ReportDocument();
        document.runId = runId;
        document.stateCode = Objects.requireNonNull(stateCode, "stateCode");
        document.fipsCode = Objects.requireNonNull(fipsCode, "fipsCode");
        document.status = status;
        document.lastError = lastError;
        document.createdAt = Objects.requireNonNull(now, "now");
        document.updatedAt = now;
        return document;
    }

    public Long getDocId() {
        return docId;
    }

    public Long getRunId() {
        return runId;
    }

    public String getStateCode() {
        return stateCode == null ? null : stateCode.trim();
    }

    public String getFipsCode() {
        return fipsCode == null ? null : fipsCode.trim();
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public int getGenerationAttempts() {
        return generationAttempts;
    }

    public int getSendAttempts() {
        return sendAttempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getJmsMessageId() {
        return jmsMessageId;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getContentPurgedAt() {
        return contentPurgedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }
}
