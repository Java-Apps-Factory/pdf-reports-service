package com.example.reporting.pdf;

import com.example.reporting.pdf.table.TableSpec;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * One category section: heading, continuation label and table columns.
 *
 * <p>The heading may contain the token {@value #COUNT_TOKEN}; it is replaced by the section's row count
 * (US grouping, for example "2,000") when the section ends, because the count is only known after the rows
 * have streamed. The resolved heading is also the section's bookmark title.
 */
public final class SectionSpec {

    /** Token in the heading replaced by the section's row count. */
    public static final String COUNT_TOKEN = "{n}";

    private final String heading;
    private final String continuationLabel;
    private final TableSpec table;

    private SectionSpec(Builder builder) {
        this.heading = Objects.requireNonNull(builder.heading, "heading");
        this.continuationLabel = Objects.requireNonNull(builder.continuationLabel, "continuationLabel");
        this.table = Objects.requireNonNull(builder.table, "table");
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
     * Returns the heading template.
     *
     * @return heading, possibly containing {@value #COUNT_TOKEN}
     */
    public String heading() {
        return heading;
    }

    /**
     * Returns the heading with the row count filled in.
     *
     * @param rowCount rows in the section
     * @return resolved heading
     */
    public String headingFor(long rowCount) {
        return heading.replace(COUNT_TOKEN, NumberFormat.getIntegerInstance(Locale.US).format(rowCount));
    }

    /**
     * Returns the label drawn at the top of continuation pages.
     *
     * @return label, for example "Fees (continued)"
     */
    public String continuationLabel() {
        return continuationLabel;
    }

    /**
     * Returns the table columns.
     *
     * @return table spec
     */
    public TableSpec table() {
        return table;
    }

    /** Builder for {@link SectionSpec}. Heading, continuation label and table are required. */
    public static final class Builder {
        private String heading;
        private String continuationLabel;
        private TableSpec table;

        private Builder() {
        }

        /**
         * Sets the heading template.
         *
         * @param value heading, optionally containing {@value SectionSpec#COUNT_TOKEN}
         * @return this builder
         */
        public Builder heading(String value) {
            this.heading = value;
            return this;
        }

        /**
         * Sets the continuation label.
         *
         * @param value label
         * @return this builder
         */
        public Builder continuationLabel(String value) {
            this.continuationLabel = value;
            return this;
        }

        /**
         * Sets the table columns.
         *
         * @param value table spec
         * @return this builder
         */
        public Builder table(TableSpec value) {
            this.table = value;
            return this;
        }

        /**
         * Builds the section spec.
         *
         * @return immutable section spec
         */
        public SectionSpec build() {
            return new SectionSpec(this);
        }
    }
}
