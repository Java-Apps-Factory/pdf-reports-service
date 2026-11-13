package com.example.reporting.profile;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Value type of a report column; decides formatting, alignment and wrapping (SPEC section 11). */
public enum ColumnType {
    /** Text as stored, trimmed; left-aligned; wrapping configurable. */
    TEXT(Object.class, false),
    /** {@code MM/dd/yyyy}; left-aligned; never wraps. */
    DATE(LocalDate.class, false),
    /** {@code $#,##0.00}, negatives as {@code -$1,234.00}; right-aligned; never wraps. */
    CURRENCY(BigDecimal.class, true),
    /** {@code #,##0}; right-aligned; never wraps. */
    INTEGER(Number.class, true);

    private final Class<?> valueType;
    private final boolean rightAligned;

    ColumnType(Class<?> valueType, boolean rightAligned) {
        this.valueType = valueType;
        this.rightAligned = rightAligned;
    }

    /**
     * Tells whether values of the given Java type can be shown in a column of this type.
     *
     * @param type Java type a row field produces
     * @return true if compatible
     */
    public boolean accepts(Class<?> type) {
        return valueType.isAssignableFrom(type);
    }

    /**
     * Tells whether cells of this type are right-aligned.
     *
     * @return true for numbers and amounts
     */
    public boolean isRightAligned() {
        return rightAligned;
    }

    /**
     * Tells whether cells of this type may wrap; only text can.
     *
     * @return true for {@link #TEXT}
     */
    public boolean canWrap() {
        return this == TEXT;
    }
}
