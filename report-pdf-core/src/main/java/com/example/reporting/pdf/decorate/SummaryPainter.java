package com.example.reporting.pdf.decorate;

import com.example.reporting.pdf.SummaryData;
import com.example.reporting.pdf.a11y.TaggingContext;
import com.example.reporting.pdf.a11y.TaggingContext.Role;
import com.example.reporting.pdf.layout.Canvas;
import com.example.reporting.pdf.layout.LayoutMetrics;
import com.example.reporting.pdf.layout.PageGeometry;
import com.example.reporting.pdf.table.Alignment;
import com.example.reporting.pdf.table.CellValue;
import com.example.reporting.pdf.table.ColumnSpec;
import com.example.reporting.pdf.table.RowLayout;
import com.example.reporting.pdf.table.TableRenderer;
import com.example.reporting.pdf.table.TableSpec;
import com.example.reporting.pdf.text.FontFace;
import com.example.reporting.pdf.text.LoadedFonts;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Draws the summary into the area reserved on page 1: heading, a Category/Records table with one row per
 * category and a bold total row (SPEC section 11). Internal to report-pdf-core.
 */
public final class SummaryPainter {

    private final PageGeometry geometry;
    private final LoadedFonts fonts;
    private final TaggingContext tagging;
    private final TableRenderer tableRenderer;

    /**
     * Creates the painter.
     *
     * @param geometry page geometry
     * @param fonts document fonts
     * @param tagging tagging context
     * @param tableRenderer table renderer
     */
    public SummaryPainter(PageGeometry geometry, LoadedFonts fonts, TaggingContext tagging,
            TableRenderer tableRenderer) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.fonts = Objects.requireNonNull(fonts, "fonts");
        this.tagging = Objects.requireNonNull(tagging, "tagging");
        this.tableRenderer = Objects.requireNonNull(tableRenderer, "tableRenderer");
    }

    /**
     * Paints the summary on page 1.
     *
     * @param page page 1
     * @param canvas canvas over page 1
     * @param summary summary rows
     * @throws IOException on a stream error
     */
    public void paint(PDPage page, Canvas canvas, SummaryData summary) throws IOException {
        float left = geometry.contentLeft();
        float top = geometry.bodyTop(1);
        FontFace bold = fonts.bold();
        FontFace regular = fonts.regular();
        NumberFormat counts = NumberFormat.getIntegerInstance(Locale.US);

        tagging.beginStructure(Role.SECT);
        String heading = bold.sanitize(summary.heading());
        float size = LayoutMetrics.HEADING_SIZE;
        tagging.beginStructure(Role.H2);
        tagging.markContent(canvas.stream(), page, () -> canvas.text(bold.font(), size, left,
                LayoutMetrics.baseline(top, LayoutMetrics.HEADING_LEADING, size), heading));
        tagging.endStructure();
        float y = top - LayoutMetrics.HEADING_LEADING - LayoutMetrics.HEADING_SPACE_BELOW;

        TableSpec table = TableSpec.builder()
                .addColumn(ColumnSpec.builder(summary.labelHeader(), LayoutMetrics.SUMMARY_LABEL_WIDTH)
                        .wrap(false).build())
                .addColumn(ColumnSpec.builder(summary.countHeader(), LayoutMetrics.SUMMARY_COUNT_WIDTH)
                        .alignment(Alignment.RIGHT).wrap(false).build())
                .build();
        float[] widths = {LayoutMetrics.SUMMARY_LABEL_WIDTH, LayoutMetrics.SUMMARY_COUNT_WIDTH};

        tagging.beginStructure(Role.TABLE);
        tagging.beginStructure(Role.THEAD);
        RowLayout header = RowLayout.of(table, List.of(CellValue.of(bold.sanitize(summary.labelHeader())),
                CellValue.of(bold.sanitize(summary.countHeader()))), widths, bold.measurer());
        tableRenderer.drawRow(canvas, page, left, y, widths, header, bold, LayoutMetrics.HEADER_FILL_GRAY,
                Role.TH, false);
        y -= header.height();
        tagging.endStructure();

        tagging.beginStructure(Role.TBODY);
        int index = 0;
        for (SummaryData.Row row : summary.rows()) {
            index++;
            RowLayout layout = RowLayout.of(table, List.of(CellValue.of(regular.sanitize(row.label())),
                    CellValue.of(counts.format(row.count()))), widths, regular.measurer());
            float fill = index % 2 == 0 ? LayoutMetrics.EVEN_ROW_FILL_GRAY : TableRenderer.NO_FILL;
            tableRenderer.drawRow(canvas, page, left, y, widths, layout, regular, fill, Role.TD, false);
            y -= layout.height();
        }
        RowLayout total = RowLayout.of(table, List.of(CellValue.of(bold.sanitize(summary.totalLabel())),
                CellValue.of(counts.format(summary.total()))), widths, bold.measurer());
        tableRenderer.drawRow(canvas, page, left, y, widths, total, bold, TableRenderer.NO_FILL, Role.TD, false);
        tagging.endStructure();
        tagging.endStructure();
        tagging.endStructure();
    }
}
