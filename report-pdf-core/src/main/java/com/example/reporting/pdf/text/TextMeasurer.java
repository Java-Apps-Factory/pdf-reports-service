package com.example.reporting.pdf.text;

import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * Measures the advance width of text in a given font size. Internal to report-pdf-core.
 *
 * <p>An interface so that wrapping and pagination can be unit-tested as pure math with a fake measurer.
 * Text passed in must already be sanitized by {@link GlyphSanitizer}.
 */
public interface TextMeasurer {

    /**
     * Returns the width of {@code text} in points.
     *
     * @param text sanitized text
     * @param fontSize font size in points
     * @return width in points
     */
    float width(String text, float fontSize);

    /**
     * Returns a measurer backed by a PDFBox font, caching glyph widths per code point.
     *
     * @param font a font loaded into the current document
     * @return a measurer for that font; not thread-safe, like the font itself
     */
    static TextMeasurer forFont(PDFont font) {
        return new PdfFontMeasurer(font);
    }
}
