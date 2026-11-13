package com.example.reporting.rendering;

import java.time.Instant;
import java.util.Objects;

/** What to render: one state and FIPS code of one run, with the run's data-as-of time. */
public final class ReportRequest {

    private final String stateCode;
    private final String fipsCode;
    private final long runId;
    private final long docId;
    private final Instant dataAsOf;

    private ReportRequest(Builder b) {
        this.stateCode = Objects.requireNonNull(b.stateCode, "stateCode");
        this.fipsCode = Objects.requireNonNull(b.fipsCode, "fipsCode");
        this.runId = b.runId;
        this.docId = b.docId;
        this.dataAsOf = Objects.requireNonNull(b.dataAsOf, "dataAsOf");
    }

    /**
     * Returns a new builder.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public String stateCode() {
        return stateCode;
    }

    public String fipsCode() {
        return fipsCode;
    }

    public long runId() {
        return runId;
    }

    public long docId() {
        return docId;
    }

    /**
     * Returns the run start time printed as "Data as of" (FR-19).
     *
     * @return instant
     */
    public Instant dataAsOf() {
        return dataAsOf;
    }

    /** Builder for {@link ReportRequest}. */
    public static final class Builder {
        private String stateCode;
        private String fipsCode;
        private long runId;
        private long docId;
        private Instant dataAsOf;

        private Builder() {
        }

        /**
         * Sets the state code.
         *
         * @param value state code
         * @return this builder
         */
        public Builder stateCode(String value) {
            this.stateCode = value;
            return this;
        }

        /**
         * Sets the FIPS code.
         *
         * @param value FIPS code
         * @return this builder
         */
        public Builder fipsCode(String value) {
            this.fipsCode = value;
            return this;
        }

        /**
         * Sets the run ID.
         *
         * @param value run ID
         * @return this builder
         */
        public Builder runId(long value) {
            this.runId = value;
            return this;
        }

        /**
         * Sets the document ID.
         *
         * @param value document ID
         * @return this builder
         */
        public Builder docId(long value) {
            this.docId = value;
            return this;
        }

        /**
         * Sets the data-as-of time.
         *
         * @param value run start time
         * @return this builder
         */
        public Builder dataAsOf(Instant value) {
            this.dataAsOf = value;
            return this;
        }

        /**
         * Builds the request.
         *
         * @return immutable request
         */
        public ReportRequest build() {
            return new ReportRequest(this);
        }
    }
}
