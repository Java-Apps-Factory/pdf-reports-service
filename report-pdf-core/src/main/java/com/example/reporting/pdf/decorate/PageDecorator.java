package com.example.reporting.pdf.decorate;

import com.example.reporting.pdf.DocumentSpec;
import com.example.reporting.pdf.SummaryData;
import com.example.reporting.pdf.a11y.TaggingContext;
import com.example.reporting.pdf.a11y.TaggingContext.Role;
import com.example.reporting.pdf.layout.Canvas;
import com.example.reporting.pdf.layout.LayoutMetrics;
import com.example.reporting.pdf.layout.PageGeometry;
import com.example.reporting.pdf.table.TableRenderer;
import com.example.reporting.pdf.text.FontFace;
import com.example.reporting.pdf.text.LoadedFonts;
import com.example.reporting.pdf.text.TextWrapper;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;

/**
 * Pass 2 (AD-06, Decorator): once the page count is known, appends to every finished page its header
 * (title block and summary on page 1, running header on pages 2+), the section headings placed on it, and the
 * footer with "Page X of Y". Internal to report-pdf-core.
 */
public final class PageDecorator {

    private final PageGeometry geometry;
    private final LoadedFonts fonts;
    private final TaggingContext tagging;
    private final TitleBlockPainter titleBlock;
    private final SummaryPainter summaryPainter;
    private final RunningHeaderPainter runningHeader;
    private final FooterPainter footer;

    /**
     * Creates the decorator.
     *
     * @param geometry page geometry
     * @param fonts document fonts
     * @param tagging tagging context
     */
    public PageDecorator(PageGeometry geometry, LoadedFonts fonts, TaggingContext tagging) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.fonts = Objects.requireNonNull(fonts, "fonts");
        this.tagging = Objects.requireNonNull(tagging, "tagging");
        this.titleBlock = new TitleBlockPainter(geometry, fonts, tagging);
        this.summaryPainter = new SummaryPainter(geometry, fonts, tagging, new TableRenderer(tagging));
        this.runningHeader = new RunningHeaderPainter(geometry, fonts, tagging);
        this.footer = new FooterPainter(geometry, fonts, tagging);
    }

    /**
     * Decorates every page of the document.
     *
     * @param document the document after pass 1
     * @param spec document spec with header and footer texts
     * @param summary summary rows for page 1
     * @param headings section headings placed in pass 1
     * @throws IOException on a stream error
     */
    public void decorate(PDDocument document, DocumentSpec spec, SummaryData summary, List<SectionHeading> headings)
            throws IOException {
        int pageCount = document.getNumberOfPages();
        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            PDPage page = document.getPage(pageNumber - 1);
            try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true,
                    true)) {
                Canvas canvas = new Canvas(stream);
                if (pageNumber == 1) {
                    titleBlock.paint(document, page, canvas, spec);
                    summaryPainter.paint(page, canvas, summary);
                } else {
                    runningHeader.paint(canvas, pageNumber, spec.runningHeaderLeft(), spec.runningHeaderRight());
                }
                for (SectionHeading heading : headings) {
                    if (heading.pageNumber() == pageNumber) {
                        paintHeading(page, canvas, heading);
                    }
                }
                footer.paint(canvas, spec.footerDisclaimer(), spec.reportId(), pageNumber, pageCount);
            }
        }
    }

    private void paintHeading(PDPage page, Canvas canvas, SectionHeading heading) throws IOException {
        FontFace bold = fonts.bold();
        float size = LayoutMetrics.HEADING_SIZE;
        String text = TextWrapper.truncate(bold.sanitize(heading.text()), bold.measurer(), size,
                geometry.contentWidth());
        float baseline = LayoutMetrics.baseline(heading.top(), LayoutMetrics.HEADING_LEADING, size);
        tagging.beginStructure(Role.H2);
        float left = geometry.contentLeft();
        tagging.markContent(canvas.stream(), page, () -> canvas.text(bold.font(), size, left, baseline, text));
        tagging.endStructure();
    }
}
