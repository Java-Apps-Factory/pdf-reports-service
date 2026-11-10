package com.example.reporting.pdf.table;

import com.example.reporting.pdf.layout.LayoutMetrics;
import com.example.reporting.pdf.text.TextMeasurer;
import com.example.reporting.pdf.text.TextWrapper;
import java.util.ArrayList;
import java.util.List;

/**
 * The measured layout of one table row: wrapped lines per cell, alignment per cell and row height.
 * Internal to report-pdf-core; pure math, testable without a PDF.
 *
 * <p>Row height = max line count × leading + vertical padding. Wrapping columns wrap to the column width minus
 * horizontal padding; no-wrap columns are cut to one line ending in an ellipsis.
 */
public final class RowLayout {

    private final List<List<String>> cellLines;
    private final List<Alignment> alignments;
    private final float[] textWidths;
    private final TextMeasurer measurer;
    private final float fontSize;
    private final boolean truncated;

    private RowLayout(List<List<String>> cellLines, List<Alignment> alignments, float[] textWidths,
            TextMeasurer measurer, float fontSize, boolean truncated) {
        this.cellLines = cellLines;
        this.alignments = alignments;
        this.textWidths = textWidths;
        this.measurer = measurer;
        this.fontSize = fontSize;
        this.truncated = truncated;
    }

    /**
     * Lays out a data row.
     *
     * @param table the table columns
     * @param cells sanitized cells, one per column
     * @param columnWidths column widths in points
     * @param measurer measurer of the font the row is drawn in
     * @return the row layout
     */
    public static RowLayout of(TableSpec table, List<CellValue> cells, float[] columnWidths,
            TextMeasurer measurer) {
        if (cells.size() != table.columnCount()) {
            throw new IllegalArgumentException(
                    "Row has " + cells.size() + " cells but the table has " + table.columnCount() + " columns");
        }
        float[] textWidths = textWidths(columnWidths);
        List<List<String>> lines = new ArrayList<>(cells.size());
        List<Alignment> aligns = new ArrayList<>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            ColumnSpec column = table.columns().get(i);
            CellValue cell = cells.get(i);
            String text = cell.text();
            float size = LayoutMetrics.TABLE_SIZE;
            lines.add(column.wrap()
                    ? TextWrapper.wrap(text, measurer, size, textWidths[i])
                    : List.of(TextWrapper.truncate(text.trim(), measurer, size, textWidths[i])));
            aligns.add(cell.alignment().orElse(column.alignment()));
        }
        return new RowLayout(lines, aligns, textWidths, measurer, LayoutMetrics.TABLE_SIZE, false);
    }

    /**
     * Lays out the header row: column labels, always wrapping, aligned like their columns.
     *
     * @param table the table columns
     * @param labels sanitized labels, one per column
     * @param columnWidths column widths in points
     * @param measurer measurer of the bold font
     * @return the header row layout
     */
    public static RowLayout header(TableSpec table, List<String> labels, float[] columnWidths,
            TextMeasurer measurer) {
        float[] textWidths = textWidths(columnWidths);
        List<List<String>> lines = new ArrayList<>(labels.size());
        List<Alignment> aligns = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            lines.add(TextWrapper.wrap(labels.get(i), measurer, LayoutMetrics.TABLE_SIZE, textWidths[i]));
            aligns.add(table.columns().get(i).alignment());
        }
        return new RowLayout(lines, aligns, textWidths, measurer, LayoutMetrics.TABLE_SIZE, false);
    }

    /**
     * Returns this row cut to fit {@code maxHeight}: every cell keeps at most the lines that fit, and a cell
     * that lost lines ends its last visible line with an ellipsis. At least one line is always kept.
     *
     * @param maxHeight the available height in points
     * @return this row if it fits, otherwise a truncated copy
     */
    public RowLayout fitTo(float maxHeight) {
        if (height() <= maxHeight) {
            return this;
        }
        int maxLines = Math.max(1,
                (int) Math.floor((maxHeight - 2 * LayoutMetrics.CELL_PADDING_Y) / LayoutMetrics.TABLE_LEADING));
        List<List<String>> cut = new ArrayList<>(cellLines.size());
        for (int i = 0; i < cellLines.size(); i++) {
            cut.add(TextWrapper.limitLines(cellLines.get(i), maxLines, measurer, fontSize, textWidths[i]));
        }
        return new RowLayout(cut, alignments, textWidths, measurer, fontSize, true);
    }

    /**
     * Returns the row height.
     *
     * @return points
     */
    public float height() {
        return LayoutMetrics.rowHeight(lineCount());
    }

    /**
     * Returns the largest line count of any cell.
     *
     * @return at least 1
     */
    public int lineCount() {
        int max = 1;
        for (List<String> lines : cellLines) {
            max = Math.max(max, lines.size());
        }
        return max;
    }

    /**
     * Returns the number of cells.
     *
     * @return cell count
     */
    public int cellCount() {
        return cellLines.size();
    }

    /**
     * Returns the lines of one cell.
     *
     * @param column 0-based column index
     * @return unmodifiable lines
     */
    public List<String> lines(int column) {
        return List.copyOf(cellLines.get(column));
    }

    /**
     * Returns the effective alignment of one cell.
     *
     * @param column 0-based column index
     * @return alignment
     */
    public Alignment alignment(int column) {
        return alignments.get(column);
    }

    /**
     * Tells whether {@link #fitTo} had to drop lines.
     *
     * @return true for a truncated row
     */
    public boolean isTruncated() {
        return truncated;
    }

    private static float[] textWidths(float[] columnWidths) {
        float[] widths = new float[columnWidths.length];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Math.max(0f, columnWidths[i] - 2 * LayoutMetrics.CELL_PADDING_X);
        }
        return widths;
    }
}
