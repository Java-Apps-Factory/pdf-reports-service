package com.example.reporting.transmission;

import java.util.Objects;
import javax.jms.BytesMessage;
import javax.jms.JMSException;

/**
 * One report message: the PDF bytes and the contract properties of SPEC section 13. The body is written
 * straight into the JMS message and never copied or exposed.
 */
public final class ReportMessage {

    private final long reportDocId;
    private final long runId;
    private final String stateCode;
    private final String fipsCode;
    private final String generatedAt;
    private final String dataAsOf;
    private final int recordCount;
    private final int pageCount;
    private final String sha256;
    private final byte[] body;

    private ReportMessage(Builder b) {
        this.reportDocId = b.reportDocId;
        this.runId = b.runId;
        this.stateCode = Objects.requireNonNull(b.stateCode, "stateCode");
        this.fipsCode = Objects.requireNonNull(b.fipsCode, "fipsCode");
        this.generatedAt = Objects.requireNonNull(b.generatedAt, "generatedAt");
        this.dataAsOf = Objects.requireNonNull(b.dataAsOf, "dataAsOf");
        this.recordCount = b.recordCount;
        this.pageCount = b.pageCount;
        this.sha256 = Objects.requireNonNull(b.sha256, "sha256");
        this.body = Objects.requireNonNull(b.body, "body");
    }

    /**
     * Returns a new builder.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public long reportDocId() {
        return reportDocId;
    }

    public long runId() {
        return runId;
    }

    public String stateCode() {
        return stateCode;
    }

    public String fipsCode() {
        return fipsCode;
    }

    public String generatedAt() {
        return generatedAt;
    }

    public String dataAsOf() {
        return dataAsOf;
    }

    public int recordCount() {
        return recordCount;
    }

    public int pageCount() {
        return pageCount;
    }

    public String sha256() {
        return sha256;
    }

    /**
     * Returns the body size.
     *
     * @return bytes
     */
    public int size() {
        return body.length;
    }

    /**
     * Writes the body and every contract property into a JMS message.
     *
     * @param message an empty bytes message
     * @throws JMSException if the provider rejects a value
     */
    public void writeTo(BytesMessage message) throws JMSException {
        message.writeBytes(body);
        message.setLongProperty(ReportMessageProperties.REPORT_DOC_ID, reportDocId);
        message.setLongProperty(ReportMessageProperties.RUN_ID, runId);
        message.setStringProperty(ReportMessageProperties.STATE_CODE, stateCode);
        message.setStringProperty(ReportMessageProperties.FIPS_CODE, fipsCode);
        message.setStringProperty(ReportMessageProperties.GENERATED_AT, generatedAt);
        message.setStringProperty(ReportMessageProperties.DATA_AS_OF, dataAsOf);
        message.setIntProperty(ReportMessageProperties.RECORD_COUNT, recordCount);
        message.setIntProperty(ReportMessageProperties.PAGE_COUNT, pageCount);
        message.setStringProperty(ReportMessageProperties.SHA256, sha256);
        message.setStringProperty(ReportMessageProperties.CONTENT_TYPE, ReportMessageProperties.CONTENT_TYPE_PDF);
        message.setIntProperty(ReportMessageProperties.SCHEMA_VERSION, ReportMessageProperties.CURRENT_SCHEMA_VERSION);
    }

    /** Builder for {@link ReportMessage}. */
    public static final class Builder {
        private long reportDocId;
        private long runId;
        private String stateCode;
        private String fipsCode;
        private String generatedAt;
        private String dataAsOf;
        private int recordCount;
        private int pageCount;
        private String sha256;
        private byte[] body;

        private Builder() {
        }

        /**
         * Sets the ids and codes.
         *
         * @param docId document ID (idempotency key)
         * @param run run ID
         * @param state state code
         * @param fips FIPS code
         * @return this builder
         */
        public Builder identity(long docId, long run, String state, String fips) {
            this.reportDocId = docId;
            this.runId = run;
            this.stateCode = state;
            this.fipsCode = fips;
            return this;
        }

        /**
         * Sets the times, ISO-8601 with offset.
         *
         * @param generated when the PDF was created
         * @param asOf data snapshot time
         * @return this builder
         */
        public Builder times(String generated, String asOf) {
            this.generatedAt = generated;
            this.dataAsOf = asOf;
            return this;
        }

        /**
         * Sets the PDF.
         *
         * @param records rows in the report
         * @param pages pages
         * @param sha hex SHA-256 of the body
         * @param pdf the PDF bytes; kept by reference, not copied
         * @return this builder
         */
        public Builder content(int records, int pages, String sha, byte[] pdf) {
            this.recordCount = records;
            this.pageCount = pages;
            this.sha256 = sha;
            this.body = pdf;
            return this;
        }

        /**
         * Builds the message.
         *
         * @return the message
         */
        public ReportMessage build() {
            return new ReportMessage(this);
        }
    }
}
