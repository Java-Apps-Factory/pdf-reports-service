package com.example.reporting.api.dto;

import java.time.OffsetDateTime;

/** A document as returned by the API (SPEC section 14). */
public final class DocumentResponse {

    private final long docId;
    private final long runId;
    private final String stateCode;
    private final String fipsCode;
    private final String localityName;
    private final String status;
    private final Integer recordCount;
    private final Integer pageCount;
    private final Long sizeBytes;
    private final String sha256;
    private final int generationAttempts;
    private final int sendAttempts;
    private final OffsetDateTime nextAttemptAt;
    private final String lastError;
    private final OffsetDateTime generatedAt;
    private final OffsetDateTime sentAt;
    private final boolean contentAvailable;

    private DocumentResponse(Builder b) {
        this.docId = b.docId;
        this.runId = b.runId;
        this.stateCode = b.stateCode;
        this.fipsCode = b.fipsCode;
        this.localityName = b.localityName;
        this.status = b.status;
        this.recordCount = b.recordCount;
        this.pageCount = b.pageCount;
        this.sizeBytes = b.sizeBytes;
        this.sha256 = b.sha256;
        this.generationAttempts = b.generationAttempts;
        this.sendAttempts = b.sendAttempts;
        this.nextAttemptAt = b.nextAttemptAt;
        this.lastError = b.lastError;
        this.generatedAt = b.generatedAt;
        this.sentAt = b.sentAt;
        this.contentAvailable = b.contentAvailable;
    }

    /**
     * Returns a new builder.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public long getDocId() {
        return docId;
    }

    public long getRunId() {
        return runId;
    }

    public String getStateCode() {
        return stateCode;
    }

    public String getFipsCode() {
        return fipsCode;
    }

    public String getLocalityName() {
        return localityName;
    }

    public String getStatus() {
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

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public boolean isContentAvailable() {
        return contentAvailable;
    }

    /** Builder for {@link DocumentResponse}. */
    public static final class Builder {
        private long docId;
        private long runId;
        private String stateCode;
        private String fipsCode;
        private String localityName;
        private String status;
        private Integer recordCount;
        private Integer pageCount;
        private Long sizeBytes;
        private String sha256;
        private int generationAttempts;
        private int sendAttempts;
        private OffsetDateTime nextAttemptAt;
        private String lastError;
        private OffsetDateTime generatedAt;
        private OffsetDateTime sentAt;
        private boolean contentAvailable;

        private Builder() {
        }

        /**
         * Sets the ids and codes.
         *
         * @param doc document ID
         * @param run run ID
         * @param state state code
         * @param fips FIPS code
         * @param locality locality name, may be {@code null}
         * @return this builder
         */
        public Builder identity(long doc, long run, String state, String fips, String locality) {
            this.docId = doc;
            this.runId = run;
            this.stateCode = state;
            this.fipsCode = fips;
            this.localityName = locality;
            return this;
        }

        /**
         * Sets the status and attempts.
         *
         * @param value status
         * @param generation generation attempts
         * @param send send attempts
         * @param next next attempt time, may be {@code null}
         * @param error last error, may be {@code null}
         * @return this builder
         */
        public Builder status(String value, int generation, int send, OffsetDateTime next, String error) {
            this.status = value;
            this.generationAttempts = generation;
            this.sendAttempts = send;
            this.nextAttemptAt = next;
            this.lastError = error;
            return this;
        }

        /**
         * Sets the PDF facts.
         *
         * @param records record count
         * @param pages page count
         * @param size size in bytes
         * @param sha SHA-256
         * @param available whether the content can be downloaded
         * @return this builder
         */
        public Builder content(Integer records, Integer pages, Long size, String sha, boolean available) {
            this.recordCount = records;
            this.pageCount = pages;
            this.sizeBytes = size;
            this.sha256 = sha;
            this.contentAvailable = available;
            return this;
        }

        /**
         * Sets the timestamps.
         *
         * @param generated generation time, may be {@code null}
         * @param sent send time, may be {@code null}
         * @return this builder
         */
        public Builder times(OffsetDateTime generated, OffsetDateTime sent) {
            this.generatedAt = generated;
            this.sentAt = sent;
            return this;
        }

        /**
         * Builds the response.
         *
         * @return immutable response
         */
        public DocumentResponse build() {
            return new DocumentResponse(this);
        }
    }
}
