package com.example.reporting.pdf.text;

import java.util.Objects;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * One loaded font of one document, with its measurer and sanitizer. Internal to report-pdf-core.
 *
 * <p>Belongs to a single document and thread; never shared.
 */
public final class FontFace {

    private final PDFont font;
    private final TextMeasurer measurer;
    private final GlyphSanitizer sanitizer;

    /**
     * Wraps a font loaded into the current document.
     *
     * @param font the PDFBox font
     */
    public FontFace(PDFont font) {
        this.font = Objects.requireNonNull(font, "font");
        this.measurer = TextMeasurer.forFont(font);
        this.sanitizer = GlyphSanitizer.forFont(font);
    }

    /**
     * Returns the PDFBox font for drawing.
     *
     * @return the document-local font
     */
    public PDFont font() {
        return font;
    }

    /**
     * Returns the measurer for this font.
     *
     * @return the measurer
     */
    public TextMeasurer measurer() {
        return measurer;
    }

    /**
     * Sanitizes text for this font.
     *
     * @param text any text, may be {@code null}
     * @return drawable text
     */
    public String sanitize(String text) {
        return sanitizer.sanitize(text);
    }

    /**
     * Measures sanitized text.
     *
     * @param text sanitized text
     * @param fontSize font size in points
     * @return width in points
     */
    public float width(String text, float fontSize) {
        return measurer.width(text, fontSize);
    }
}
