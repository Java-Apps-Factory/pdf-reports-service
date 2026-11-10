package com.example.reporting.pdf.decorate;

import com.example.reporting.pdf.a11y.TaggingContext;
import com.example.reporting.pdf.a11y.TaggingContext.ArtifactType;
import com.example.reporting.pdf.layout.Canvas;
import com.example.reporting.pdf.layout.LayoutMetrics;
import com.example.reporting.pdf.layout.PageGeometry;
import com.example.reporting.pdf.text.FontFace;
import com.example.reporting.pdf.text.LoadedFonts;
import com.example.reporting.pdf.text.TextWrapper;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Draws the footer on every page: 0.5 pt rule above, disclaimer on the left (at most two lines, then an
 * ellipsis), report ID in the centre and "Page X of Y" on the right. A pagination artifact. Internal to
 * report-pdf-core.
 */
public final class FooterPainter {

    private static final int MAX_DISCLAIMER_LINES = 2;
    private static final float CENTER_ZONE_HALF_WIDTH = 90f;
    private static final float RULE_GAP = 4f;

    private final PageGeometry geometry;
    private final LoadedFonts fonts;
    private final TaggingContext tagging;

    /**
     * Creates the painter.
     *
     * @param geometry page geometry
     * @param fonts document fonts
     * @param tagging tagging context
     */
    public FooterPainter(PageGeometry geometry, LoadedFonts fonts, TaggingContext tagging) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.fonts = Objects.requireNonNull(fonts, "fonts");
        this.tagging = Objects.requireNonNull(tagging, "tagging");
    }

    /**
     * Returns the page label, for example "Page 3 of 12".
     *
     * @param pageNumber 1-based page number
     * @param pageCount total pages
     * @return the label
     */
    public static String pageLabel(int pageNumber, int pageCount) {
        return String.format(Locale.US, "Page %d of %d", pageNumber, pageCount);
    }

    /**
     * Paints the footer.
     *
     * @param canvas canvas over the page
     * @param disclaimer footer disclaimer
     * @param reportId report ID shown in the centre
     * @param pageNumber 1-based page number
     * @param pageCount total pages
     * @throws IOException on a stream error
     */
    public void paint(Canvas canvas, String disclaimer, String reportId, int pageNumber, int pageCount)
            throws IOException {
        float left = geometry.contentLeft();
        float right = left + geometry.contentWidth();
        float center = left + geometry.contentWidth() / 2f;
        float ruleY = geometry.bodyBottom() - RULE_GAP;
        float textTop = ruleY - RULE_GAP;

        tagging.beginArtifact(canvas.stream(), ArtifactType.PAGINATION);
        canvas.line(left, ruleY, right, ruleY, LayoutMetrics.THIN_RULE_WIDTH, LayoutMetrics.TEXT_GRAY);

        FontFace regular = fonts.regular();
        float small = LayoutMetrics.DISCLAIMER_SIZE;
        float smallLeading = small * LayoutMetrics.LINE_SPACING;
        float disclaimerWidth = center - CENTER_ZONE_HALF_WIDTH - left;
        List<String> lines = TextWrapper.limitLines(
                TextWrapper.wrap(regular.sanitize(disclaimer), regular.measurer(), small, disclaimerWidth),
                MAX_DISCLAIMER_LINES, regular.measurer(), small, disclaimerWidth);
        float lineTop = textTop;
        for (String line : lines) {
            canvas.text(regular.font(), small, left, LayoutMetrics.baseline(lineTop, smallLeading, small), line);
            lineTop -= smallLeading;
        }

        float size = LayoutMetrics.FOOTER_SIZE;
        float baseline = LayoutMetrics.baseline(textTop, size * LayoutMetrics.LINE_SPACING, size);
        String id = TextWrapper.truncate(regular.sanitize(reportId), regular.measurer(), size,
                2 * CENTER_ZONE_HALF_WIDTH);
        canvas.text(regular.font(), size, center - regular.width(id, size) / 2f, baseline, id);
        String label = pageLabel(pageNumber, pageCount);
        canvas.text(regular.font(), size, right - regular.width(label, size), baseline, label);
        tagging.endArtifact(canvas.stream());
    }
}
