package com.example.reporting.pdf.table;

import com.example.reporting.pdf.a11y.TaggingContext;
import com.example.reporting.pdf.a11y.TaggingContext.ArtifactType;
import com.example.reporting.pdf.a11y.TaggingContext.Role;
import com.example.reporting.pdf.a11y.TaggingContext.Scope;
import com.example.reporting.pdf.layout.Canvas;
import com.example.reporting.pdf.layout.LayoutMetrics;
import com.example.reporting.pdf.text.FontFace;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Draws laid-out table rows: optional fill, cell borders and aligned text. Internal to report-pdf-core.
 *
 * <p>Fills and borders are layout artifacts. Cell text is tagged as {@code TH} (with a scope) or {@code TD}
 * inside a {@code TR}, unless the whole row is an artifact (a repeated header row on a continuation page).
 */
public final class TableRenderer {

    /** Pass as fill to draw a row without background. */
    public static final float NO_FILL = -1f;

    private final TaggingContext tagging;

    /**
     * Creates a renderer.
     *
     * @param tagging the document's tagging context
     */
    public TableRenderer(TaggingContext tagging) {
        this.tagging = Objects.requireNonNull(tagging, "tagging");
    }

    /**
     * Draws one row with its top edge at {@code top}.
     *
     * @param canvas the page canvas
     * @param page the page
     * @param left x of the table's left edge
     * @param top y of the row's top edge
     * @param columnWidths column widths in points
     * @param row the laid-out row
     * @param face font face for the text
     * @param fillGray background gray level, or {@link #NO_FILL}
     * @param kind how the row is tagged
     * @throws IOException on a stream error
     */
    public void drawRow(Canvas canvas, PDPage page, float left, float top, float[] columnWidths, RowLayout row,
            FontFace face, float fillGray, RowKind kind) throws IOException {
        float height = row.height();
        float width = sum(columnWidths);
        tagging.beginArtifact(canvas.stream(), ArtifactType.LAYOUT);
        if (fillGray >= 0f) {
            canvas.fillRect(left, top - height, width, height, fillGray);
        }
        float x = left;
        for (float columnWidth : columnWidths) {
            canvas.strokeRect(x, top - height, columnWidth, height, LayoutMetrics.BORDER_WIDTH,
                    LayoutMetrics.BORDER_GRAY);
            x += columnWidth;
        }
        tagging.endArtifact(canvas.stream());

        if (kind == RowKind.REPEATED_HEADER) {
            tagging.beginArtifact(canvas.stream(), ArtifactType.PAGINATION);
            drawCells(canvas, left, top, columnWidths, row, face);
            tagging.endArtifact(canvas.stream());
            return;
        }
        tagging.beginStructure(Role.TR);
        float cellLeft = left;
        for (int i = 0; i < row.cellCount(); i++) {
            final float cellX = cellLeft;
            final int column = i;
            openCell(kind, i);
            float cellWidth = columnWidths[i];
            tagging.markContent(canvas.stream(), page, () -> drawCell(canvas, cellX, top, cellWidth,
                    row.lines(column), row.alignment(column), face));
            tagging.endStructure();
            cellLeft += columnWidths[i];
        }
        tagging.endStructure();
    }

    private void openCell(RowKind kind, int column) {
        if (kind == RowKind.HEADER) {
            tagging.beginHeaderCell(Scope.COLUMN);
        } else if (kind == RowKind.TOTAL && column == 0) {
            tagging.beginHeaderCell(Scope.ROW);
        } else {
            tagging.beginStructure(Role.TD);
        }
    }

    private static void drawCells(Canvas canvas, float left, float top, float[] columnWidths, RowLayout row,
            FontFace face) throws IOException {
        float x = left;
        for (int i = 0; i < row.cellCount(); i++) {
            drawCell(canvas, x, top, columnWidths[i], row.lines(i), row.alignment(i), face);
            x += columnWidths[i];
        }
    }

    private static void drawCell(Canvas canvas, float x, float top, float width, List<String> lines,
            Alignment alignment, FontFace face) throws IOException {
        float size = LayoutMetrics.TABLE_SIZE;
        float lineTop = top - LayoutMetrics.CELL_PADDING_Y;
        for (String line : lines) {
            float textX = alignedX(x, width, face.width(line, size), alignment);
            canvas.text(face.font(), size, textX, LayoutMetrics.baseline(lineTop, LayoutMetrics.TABLE_LEADING, size),
                    line);
            lineTop -= LayoutMetrics.TABLE_LEADING;
        }
    }

    /**
     * Returns the x where text of the given width starts inside a cell.
     *
     * @param cellLeft x of the cell's left edge
     * @param cellWidth cell width
     * @param textWidth text width
     * @param alignment alignment
     * @return text start x
     */
    public static float alignedX(float cellLeft, float cellWidth, float textWidth, Alignment alignment) {
        switch (alignment) {
            case RIGHT:
                return cellLeft + cellWidth - LayoutMetrics.CELL_PADDING_X - textWidth;
            case CENTER:
                return cellLeft + (cellWidth - textWidth) / 2f;
            case LEFT:
            default:
                return cellLeft + LayoutMetrics.CELL_PADDING_X;
        }
    }

    private static float sum(float[] values) {
        float total = 0f;
        for (float value : values) {
            total += value;
        }
        return total;
    }
}
