package com.example.reporting.pdf.table;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The columns of a table. Column widths are the ratios scaled to the available width. */
public final class TableSpec {

    private final List<ColumnSpec> columns;

    private TableSpec(Builder builder) {
        if (builder.columns.isEmpty()) {
            throw new IllegalArgumentException("A table needs at least one column");
        }
        this.columns = List.copyOf(builder.columns);
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
     * Returns the columns in display order.
     *
     * @return unmodifiable list
     */
    public List<ColumnSpec> columns() {
        return columns;
    }

    /**
     * Returns the number of columns.
     *
     * @return column count
     */
    public int columnCount() {
        return columns.size();
    }

    /**
     * Distributes {@code totalWidth} over the columns in proportion to their ratios.
     *
     * @param totalWidth available width in points
     * @return one width per column, summing to {@code totalWidth}
     */
    public float[] columnWidths(float totalWidth) {
        float ratioSum = 0f;
        for (ColumnSpec column : columns) {
            ratioSum += column.widthRatio();
        }
        float[] widths = new float[columns.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = totalWidth * columns.get(i).widthRatio() / ratioSum;
        }
        return widths;
    }

    /** Builder for {@link TableSpec}. */
    public static final class Builder {
        private final List<ColumnSpec> columns = new ArrayList<>();

        private Builder() {
        }

        /**
         * Appends a column.
         *
         * @param column the column
         * @return this builder
         */
        public Builder addColumn(ColumnSpec column) {
            columns.add(Objects.requireNonNull(column, "column"));
            return this;
        }

        /**
         * Builds the table spec.
         *
         * @return immutable table spec
         */
        public TableSpec build() {
            return new TableSpec(this);
        }
    }
}
