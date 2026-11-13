package com.example.reporting.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One source record of a report, as read by {@code sql/report-rows.sql}. Contains personal data: never log
 * it or put it in exception messages (NFR-13).
 *
 * <p>{@link #displayOrder()} is empty when the record's category is not an active category of the state.
 */
public final class ReportRow {

    private final Integer displayOrder;
    private final String categoryCode;
    private final String caseNumber;
    private final String lastName;
    private final String firstName;
    private final LocalDate eventDate;
    private final BigDecimal amount;
    private final String status;
    private final String description;

    private ReportRow(Builder b) {
        this.displayOrder = b.displayOrder;
        this.categoryCode = Objects.requireNonNull(b.categoryCode, "categoryCode");
        this.caseNumber = Objects.requireNonNull(b.caseNumber, "caseNumber");
        this.lastName = b.lastName;
        this.firstName = b.firstName;
        this.eventDate = b.eventDate;
        this.amount = b.amount;
        this.status = b.status;
        this.description = b.description;
    }

    /**
     * Returns a new builder.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the category's display order.
     *
     * @return the order, or empty for an unknown or inactive category
     */
    public Optional<Integer> displayOrder() {
        return Optional.ofNullable(displayOrder);
    }

    public String categoryCode() {
        return categoryCode;
    }

    /**
     * Returns the value of a report column key (see {@link ReportRowMapper#FIELD_TYPES}).
     *
     * @param key column key, for example {@code lastName}
     * @return the value, or empty when the database value is null
     * @throws IllegalArgumentException for an unknown key
     */
    public Optional<Object> value(String key) {
        switch (key) {
            case ReportRowMapper.CASE_NUMBER:
                return Optional.of(caseNumber);
            case ReportRowMapper.LAST_NAME:
                return Optional.ofNullable(lastName);
            case ReportRowMapper.FIRST_NAME:
                return Optional.ofNullable(firstName);
            case ReportRowMapper.EVENT_DATE:
                return Optional.ofNullable(eventDate);
            case ReportRowMapper.AMOUNT:
                return Optional.ofNullable(amount);
            case ReportRowMapper.STATUS:
                return Optional.ofNullable(status);
            case ReportRowMapper.DESCRIPTION:
                return Optional.ofNullable(description);
            default:
                throw new IllegalArgumentException("Unknown column key " + key);
        }
    }

    @Override
    public String toString() {
        // Never print record values (NFR-13).
        return "ReportRow[category=" + categoryCode + "]";
    }

    /** Builder for {@link ReportRow}. Category code and case number are required. */
    public static final class Builder {
        private Integer displayOrder;
        private String categoryCode;
        private String caseNumber;
        private String lastName;
        private String firstName;
        private LocalDate eventDate;
        private BigDecimal amount;
        private String status;
        private String description;

        private Builder() {
        }

        /**
         * Sets the display order.
         *
         * @param value order, or {@code null} for an unknown category
         * @return this builder
         */
        public Builder displayOrder(Integer value) {
            this.displayOrder = value;
            return this;
        }

        /**
         * Sets the category code.
         *
         * @param value category code
         * @return this builder
         */
        public Builder categoryCode(String value) {
            this.categoryCode = value;
            return this;
        }

        /**
         * Sets the case number.
         *
         * @param value case number
         * @return this builder
         */
        public Builder caseNumber(String value) {
            this.caseNumber = value;
            return this;
        }

        /**
         * Sets the last name.
         *
         * @param value last name, may be {@code null}
         * @return this builder
         */
        public Builder lastName(String value) {
            this.lastName = value;
            return this;
        }

        /**
         * Sets the first name.
         *
         * @param value first name, may be {@code null}
         * @return this builder
         */
        public Builder firstName(String value) {
            this.firstName = value;
            return this;
        }

        /**
         * Sets the event date.
         *
         * @param value date, may be {@code null}
         * @return this builder
         */
        public Builder eventDate(LocalDate value) {
            this.eventDate = value;
            return this;
        }

        /**
         * Sets the amount.
         *
         * @param value amount, may be {@code null}
         * @return this builder
         */
        public Builder amount(BigDecimal value) {
            this.amount = value;
            return this;
        }

        /**
         * Sets the status.
         *
         * @param value status, may be {@code null}
         * @return this builder
         */
        public Builder status(String value) {
            this.status = value;
            return this;
        }

        /**
         * Sets the description.
         *
         * @param value description, may be {@code null}
         * @return this builder
         */
        public Builder description(String value) {
            this.description = value;
            return this;
        }

        /**
         * Builds the row.
         *
         * @return immutable row
         */
        public ReportRow build() {
            return new ReportRow(this);
        }
    }
}
