package com.example.reporting.pdf.text;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * Makes arbitrary record text safe to measure and draw with one font. Internal to report-pdf-core.
 *
 * <p>Tabs, carriage returns and line feeds become spaces; every other control character, and every character
 * the font has no glyph for, becomes {@code ?}. PDFBox therefore never throws on unusual input. Results of the
 * glyph check are cached per code point; an instance belongs to one document and is not thread-safe.
 */
public final class GlyphSanitizer {

    private static final int REPLACEMENT = '?';

    private final IntPredicate canRender;
    private final Map<Integer, Boolean> cache = new HashMap<>();

    /**
     * Creates a sanitizer from a glyph-availability test.
     *
     * @param canRender returns true when the font can draw the code point
     */
    public GlyphSanitizer(IntPredicate canRender) {
        this.canRender = Objects.requireNonNull(canRender, "canRender");
    }

    /**
     * Creates a sanitizer for a PDFBox font loaded into the current document.
     *
     * @param font the font text will be drawn with
     * @return a sanitizer for that font
     */
    public static GlyphSanitizer forFont(PDFont font) {
        Objects.requireNonNull(font, "font");
        return new GlyphSanitizer(codePoint -> canEncode(font, codePoint));
    }

    /**
     * Returns {@code text} with whitespace controls turned into spaces and unrenderable characters replaced.
     *
     * @param text any text; {@code null} is treated as empty
     * @return sanitized text, never {@code null}
     */
    public String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            out.appendCodePoint(map(codePoint));
            i += Character.charCount(codePoint);
        }
        return out.toString();
    }

    private int map(int codePoint) {
        if (codePoint == '\t' || codePoint == '\n' || codePoint == '\r') {
            return ' ';
        }
        if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT) {
            return REPLACEMENT;
        }
        return cache.computeIfAbsent(codePoint, canRender::test) ? codePoint : REPLACEMENT;
    }

    private static boolean canEncode(PDFont font, int codePoint) {
        try {
            font.encode(new String(Character.toChars(codePoint)));
            return true;
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
    }
}
