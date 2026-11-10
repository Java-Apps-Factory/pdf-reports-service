package com.example.reporting.pdf.text;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.font.PDFont;

/** {@link TextMeasurer} over a PDFBox font with a per-code-point width cache (one per document). */
final class PdfFontMeasurer implements TextMeasurer {

    private static final float GLYPH_UNITS = 1000f;

    private final PDFont font;
    private final Map<Integer, Float> widthCache = new HashMap<>();

    PdfFontMeasurer(PDFont font) {
        this.font = Objects.requireNonNull(font, "font");
    }

    @Override
    public float width(String text, float fontSize) {
        float units = 0f;
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            units += widthCache.computeIfAbsent(codePoint, this::glyphWidth);
            i += Character.charCount(codePoint);
        }
        return units / GLYPH_UNITS * fontSize;
    }

    private float glyphWidth(int codePoint) {
        try {
            return font.getStringWidth(new String(Character.toChars(codePoint)));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot measure glyph U+" + Integer.toHexString(codePoint), e);
        }
    }
}
