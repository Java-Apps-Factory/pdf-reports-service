package com.example.reporting.pdf.decorate;

import com.example.reporting.pdf.DocumentSpec;
import com.example.reporting.pdf.a11y.TaggingContext;
import com.example.reporting.pdf.a11y.TaggingContext.ArtifactType;
import com.example.reporting.pdf.a11y.TaggingContext.Role;
import com.example.reporting.pdf.layout.Canvas;
import com.example.reporting.pdf.layout.LayoutMetrics;
import com.example.reporting.pdf.layout.PageGeometry;
import com.example.reporting.pdf.text.FontFace;
import com.example.reporting.pdf.text.LoadedFonts;
import com.example.reporting.pdf.text.TextWrapper;
import java.io.IOException;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Draws the page 1 title block: logo on the left (at most 144 × 54 pt, aspect kept), then title, subtitle,
 * FIPS line and info lines, and a 0.75 pt rule below (SPEC section 11). Internal to report-pdf-core.
 */
public final class TitleBlockPainter {

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
    public TitleBlockPainter(PageGeometry geometry, LoadedFonts fonts, TaggingContext tagging) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.fonts = Objects.requireNonNull(fonts, "fonts");
        this.tagging = Objects.requireNonNull(tagging, "tagging");
    }

    /**
     * Paints the title block on page 1.
     *
     * @param document the document
     * @param page page 1
     * @param canvas canvas over page 1
     * @param spec document spec with the title block content
     * @throws IOException on a stream or image error
     */
    public void paint(PDDocument document, PDPage page, Canvas canvas, DocumentSpec spec) throws IOException {
        float top = geometry.headerTop();
        float left = geometry.contentLeft();
        float textLeft = left + LayoutMetrics.LOGO_MAX_WIDTH + LayoutMetrics.LOGO_GAP;
        float textWidth = geometry.contentLeft() + geometry.contentWidth() - textLeft;
        // Reading order of SPEC section 12: title (H1), logo (Figure), then the other lines (P).
        float lineTop = line(page, canvas, Role.H1, fonts.bold(), LayoutMetrics.TITLE_SIZE, spec.title(), textLeft,
                top, textWidth);
        paintLogo(document, page, canvas, spec, left, top);
        lineTop = line(page, canvas, Role.P, fonts.regular(), LayoutMetrics.SUBTITLE_SIZE, spec.subtitle(),
                textLeft, lineTop, textWidth);
        lineTop = line(page, canvas, Role.P, fonts.bold(), LayoutMetrics.FIPS_LINE_SIZE, spec.fipsLine(),
                textLeft, lineTop, textWidth);
        for (String info : spec.infoLines()) {
            lineTop = line(page, canvas, Role.P, fonts.regular(), LayoutMetrics.INFO_LINE_SIZE, info, textLeft,
                    lineTop, textWidth);
        }

        float ruleY = geometry.bodyTop(1) + LayoutMetrics.HEADING_SPACE_BELOW;
        tagging.beginArtifact(canvas.stream(), ArtifactType.LAYOUT);
        canvas.line(left, ruleY, left + geometry.contentWidth(), ruleY, LayoutMetrics.TITLE_RULE_WIDTH,
                LayoutMetrics.TEXT_GRAY);
        tagging.endArtifact(canvas.stream());
    }

    private void paintLogo(PDDocument document, PDPage page, Canvas canvas, DocumentSpec spec, float left,
            float top) throws IOException {
        PDImageXObject logo = LogoImages.load(document, spec.logo());
        float scale = Math.min(LayoutMetrics.LOGO_MAX_WIDTH / logo.getWidth(),
                LayoutMetrics.LOGO_MAX_HEIGHT / logo.getHeight());
        float width = logo.getWidth() * scale;
        float height = logo.getHeight() * scale;
        tagging.beginFigure(spec.logoAltText());
        tagging.markContent(canvas.stream(), page, () -> canvas.image(logo, left, top - height, width, height));
        tagging.endStructure();
    }

    private float line(PDPage page, Canvas canvas, Role role, FontFace face, float size, String text, float left,
            float top, float maxWidth) throws IOException {
        String clean = face.sanitize(text);
        if (clean.isEmpty()) {
            return top;
        }
        float leading = size * LayoutMetrics.LINE_SPACING;
        String fitted = TextWrapper.truncate(clean, face.measurer(), size, maxWidth);
        PDFont font = face.font();
        tagging.beginStructure(role);
        float baseline = LayoutMetrics.baseline(top, leading, size);
        tagging.markContent(canvas.stream(), page, () -> canvas.text(font, size, left, baseline, fitted));
        tagging.endStructure();
        return top - leading;
    }
}
