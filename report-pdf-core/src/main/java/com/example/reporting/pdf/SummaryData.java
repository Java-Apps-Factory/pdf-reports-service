package com.example.reporting.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Summary table content for page 1: one row per category (including zero counts) and a total. The caller
 * computes the counts from the same row stream that rendered the tables (AD-05).
 */
public final class SummaryData {

    private final String heading;
    private final String labelHeader;
    private final String countHeader;
    private final String totalLabel;
    private final List<Row> rows;

    private SummaryData(Builder builder) {
        this.heading = Objects.requireNonNull(builder.heading, "heading");
        this.labelHeader = Objects.requireNonNull(builder.labelHeader, "labelHeader");
        this.countHeader = Objects.requireNonNull(builder.countHeader, "countHeader");
        this.totalLabel = Objects.requireNonNull(builder.totalLabel, "totalLabel");
        this.rows = List.copyOf(builder.rows);
    }

    /**
     * Returns a builder with the default texts "Summary", "Category", "Records" and "Total".
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the heading above the summary table.
     *
     * @return heading; also the title of the first bookmark
     */
    public String heading() {
        return heading;
    }

    /**
     * Returns the label column header.
     *
     * @return header text
     */
    public String labelHeader() {
        return labelHeader;
    }

    /**
     * Returns the count column header.
     *
     * @return header text
     */
    public String countHeader() {
        return countHeader;
    }

    /**
     * Returns the label of the total row.
     *
     * @return label
     */
    public String totalLabel() {
        return totalLabel;
    }

    /**
     * Returns the category rows in display order.
     *
     * @return unmodifiable list
     */
    public List<Row> rows() {
        return rows;
    }

    /**
     * Returns the sum of all row counts.
     *
     * @return total
     */
    public long total() {
        long total = 0;
        for (Row row : rows) {
            total += row.count();
        }
        return total;
    }

    /** One summary row: a category label and its record count. */
    public static final class Row {
        private final String label;
        private final long count;

        private Row(String label, long count) {
            this.label = Objects.requireNonNull(label, "label");
            if (count < 0) {
                throw new IllegalArgumentException("count must be >= 0");
            }
            this.count = count;
        }

        /**
         * Returns the category label.
         *
         * @return label
         */
        public String label() {
            return label;
        }

        /**
         * Returns the record count.
         *
         * @return count
         */
        public long count() {
            return count;
        }
    }

    /** Builder for {@link SummaryData}. */
    public static final class Builder {
        private String heading = "Summary";
        private String labelHeader = "Category";
        private String countHeader = "Records";
        private String totalLabel = "Total";
        private final List<Row> rows = new ArrayList<>();

        private Builder() {
        }

        /**
         * Sets the heading.
         *
         * @param value heading text
         * @return this builder
         */
        public Builder heading(String value) {
            this.heading = value;
            return this;
        }

        /**
         * Sets the label column header.
         *
         * @param value header text
         * @return this builder
         */
        public Builder labelHeader(String value) {
            this.labelHeader = value;
            return this;
        }

        /**
         * Sets the count column header.
         *
         * @param value header text
         * @return this builder
         */
        public Builder countHeader(String value) {
            this.countHeader = value;
            return this;
        }

        /**
         * Sets the total row label.
         *
         * @param value label
         * @return this builder
         */
        public Builder totalLabel(String value) {
            this.totalLabel = value;
            return this;
        }

        /**
         * Appends a category row.
         *
         * @param label category label
         * @param count record count, at least 0
         * @return this builder
         */
        public Builder addRow(String label, long count) {
            rows.add(new Row(label, count));
            return this;
        }

        /**
         * Builds the summary.
         *
         * @return immutable summary
         */
        public SummaryData build() {
            return new SummaryData(this);
        }
    }
}
