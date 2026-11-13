package com.example.reporting.data;

import java.util.Objects;

/** One row of the workload query: a FIPS code with records, its record count, and whether it is unknown. */
public final class FipsWorkload {

    private final String fipsCode;
    private final long recordCount;
    private final boolean unknownFips;

    private FipsWorkload(Builder b) {
        this.fipsCode = Objects.requireNonNull(b.fipsCode, "fipsCode");
        if (b.recordCount < 0) {
            throw new IllegalArgumentException("recordCount must be >= 0");
        }
        this.recordCount = b.recordCount;
        this.unknownFips = b.unknownFips;
    }

    /**
     * Returns a new builder.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public String fipsCode() {
        return fipsCode;
    }

    public long recordCount() {
        return recordCount;
    }

    /**
     * Tells whether the FIPS code is missing from {@code RPT_FIPS} for the state (FR-20).
     *
     * @return true for an unknown FIPS code
     */
    public boolean unknownFips() {
        return unknownFips;
    }

    /** Builder for {@link FipsWorkload}. */
    public static final class Builder {
        private String fipsCode;
        private long recordCount;
        private boolean unknownFips;

        private Builder() {
        }

        /**
         * Sets the FIPS code.
         *
         * @param value five-digit code
         * @return this builder
         */
        public Builder fipsCode(String value) {
            this.fipsCode = value;
            return this;
        }

        /**
         * Sets the record count.
         *
         * @param value records
         * @return this builder
         */
        public Builder recordCount(long value) {
            this.recordCount = value;
            return this;
        }

        /**
         * Sets the unknown flag.
         *
         * @param value true if not in RPT_FIPS
         * @return this builder
         */
        public Builder unknownFips(boolean value) {
            this.unknownFips = value;
            return this;
        }

        /**
         * Builds the workload row.
         *
         * @return immutable value
         */
        public FipsWorkload build() {
            return new FipsWorkload(this);
        }
    }
}
