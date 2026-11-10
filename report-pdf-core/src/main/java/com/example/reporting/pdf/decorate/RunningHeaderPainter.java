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
import java.util.Objects;

/**
 * Draws the compact running header on pages 2+: bold text on the left, plain text on the right, 0.5 pt rule
 * below. The whole header is a pagination artifact. Internal to report-pdf-core.
 */
public final class RunningHeaderPainter {

    private static final float GAP = 12f;

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
    public RunningHeaderPainter(PageGeometry geometry, LoadedFonts fonts, TaggingContext tagging) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.fonts = Objects.requireNonNull(fonts, "fonts");
        this.tagging = Objects.requireNonNull(tagging, "tagging");
    }

    /**
     * Paints the running header.
     *
     * @param canvas canvas over the page
     * @param pageNumber 1-based page number, 2 or more
     * @param leftText left text
     * @param rightText right text
     * @throws IOException on a stream error
     */
    public void paint(Canvas canvas, int pageNumber, String leftText, String rightText) throws IOException {
        float size = LayoutMetrics.RUNNING_HEADER_SIZE;
        float baseline = LayoutMetrics.baseline(geometry.headerTop(), size * LayoutMetrics.LINE_SPACING, size);
        float left = geometry.contentLeft();
        float right = left + geometry.contentWidth();

        FontFace regular = fonts.regular();
        String rightClean = TextWrapper.truncate(regular.sanitize(rightText), regular.measurer(), size,
                geometry.contentWidth() / 2f);
        float rightWidth = regular.width(rightClean, size);
        FontFace bold = fonts.bold();
        String leftClean = TextWrapper.truncate(bold.sanitize(leftText), bold.measurer(), size,
                geometry.contentWidth() - rightWidth - GAP);

        tagging.beginArtifact(canvas.stream(), ArtifactType.PAGINATION);
        canvas.text(bold.font(), size, left, baseline, leftClean);
        canvas.text(regular.font(), size, right - rightWidth, baseline, rightClean);
        float ruleY = geometry.bodyTop(pageNumber) + LayoutMetrics.HEADING_SPACE_BELOW;
        canvas.line(left, ruleY, right, ruleY, LayoutMetrics.THIN_RULE_WIDTH, LayoutMetrics.TEXT_GRAY);
        tagging.endArtifact(canvas.stream());
    }
}
