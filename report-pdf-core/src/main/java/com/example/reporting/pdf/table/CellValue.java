package com.example.reporting.pdf.table;

import java.util.Objects;
import java.util.Optional;

/**
 * One already-formatted table cell: display text plus an optional alignment override.
 *
 * <p>Formatting dates, amounts and nulls is the caller's job; the core only draws text.
 */
public final class CellValue {

    private static final CellValue EMPTY = new CellValue("", null);

    private final String text;
    private final Alignment alignment;

    private CellValue(String text, Alignment alignment) {
        this.text = text;
        this.alignment = alignment;
    }

    /**
     * Creates a cell aligned like its column.
     *
     * @param text display text; {@code null} gives an empty cell
     * @return the cell
     */
    public static CellValue of(String text) {
        return text == null || text.isEmpty() ? EMPTY : new CellValue(text, null);
    }

    /**
     * Creates a cell with its own alignment.
     *
     * @param text display text; {@code null} gives an empty cell
     * @param alignment alignment overriding the column's
     * @return the cell
     */
    public static CellValue of(String text, Alignment alignment) {
        return new CellValue(text == null ? "" : text, Objects.requireNonNull(alignment, "alignment"));
    }

    /**
     * Returns an empty cell.
     *
     * @return the empty cell
     */
    public static CellValue empty() {
        return EMPTY;
    }

    /**
     * Returns the display text.
     *
     * @return text, never {@code null}
     */
    public String text() {
        return text;
    }

    /**
     * Returns the alignment override, if any.
     *
     * @return the override, or empty to use the column alignment
     */
    public Optional<Alignment> alignment() {
        return Optional.ofNullable(alignment);
    }
}
