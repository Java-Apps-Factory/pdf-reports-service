package com.example.reporting.pdf.table;

import java.util.Objects;

/** One table column: header label, relative width, alignment and whether text wraps. */
public final class ColumnSpec {

    private final String label;
    private final float widthRatio;
    private final Alignment alignment;
    private final boolean wrap;

    private ColumnSpec(Builder builder) {
        this.label = Objects.requireNonNull(builder.label, "label");
        if (!(builder.widthRatio > 0) || Float.isInfinite(builder.widthRatio)) {
            throw new IllegalArgumentException("widthRatio must be > 0");
        }
        this.widthRatio = builder.widthRatio;
        this.alignment = Objects.requireNonNull(builder.alignment, "alignment");
        this.wrap = builder.wrap;
    }

    /**
     * Returns a builder; alignment defaults to {@link Alignment#LEFT} and wrapping to on.
     *
     * @param label header label
     * @param widthRatio relative width, compared with the other columns' ratios
     * @return a builder
     */
    public static Builder builder(String label, float widthRatio) {
        return new Builder(label, widthRatio);
    }

    /**
     * Returns the header label.
     *
     * @return label
     */
    public String label() {
        return label;
    }

    /**
     * Returns the relative width.
     *
     * @return ratio greater than 0
     */
    public float widthRatio() {
        return widthRatio;
    }

    /**
     * Returns the default alignment of the column's cells.
     *
     * @return alignment
     */
    public Alignment alignment() {
        return alignment;
    }

    /**
     * Tells whether text wraps; otherwise it is truncated with an ellipsis.
     *
     * @return true if text wraps
     */
    public boolean wrap() {
        return wrap;
    }

    /** Builder for {@link ColumnSpec}. */
    public static final class Builder {
        private final String label;
        private final float widthRatio;
        private Alignment alignment = Alignment.LEFT;
        private boolean wrap = true;

        private Builder(String label, float widthRatio) {
            this.label = label;
            this.widthRatio = widthRatio;
        }

        /**
         * Sets the alignment.
         *
         * @param value alignment
         * @return this builder
         */
        public Builder alignment(Alignment value) {
            this.alignment = value;
            return this;
        }

        /**
         * Sets whether text wraps.
         *
         * @param value true to wrap, false to truncate with an ellipsis
         * @return this builder
         */
        public Builder wrap(boolean value) {
            this.wrap = value;
            return this;
        }

        /**
         * Builds the column.
         *
         * @return immutable column
         */
        public ColumnSpec build() {
            return new ColumnSpec(this);
        }
    }
}
