package com.example.reporting.pdf.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Line breaking and ellipsis truncation for one cell or text line. Internal to report-pdf-core.
 *
 * <p>Wrapping is greedy on spaces; a word wider than the line is broken between characters. All methods
 * expect text already sanitized by {@link GlyphSanitizer} and always return at least one line.
 */
public final class TextWrapper {

    /** The ellipsis appended to truncated text. */
    public static final String ELLIPSIS = "…";

    private TextWrapper() {
    }

    /**
     * Wraps text to lines no wider than {@code maxWidth}.
     *
     * @param text sanitized text
     * @param measurer measures the font in use
     * @param fontSize font size in points
     * @param maxWidth available width in points
     * @return the lines, at least one (possibly empty)
     */
    public static List<String> wrap(String text, TextMeasurer measurer, float fontSize, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split(" +")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (measurer.width(candidate, fontSize) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            String rest = word;
            while (measurer.width(rest, fontSize) > maxWidth) {
                int cut = fittingPrefixLength(rest, measurer, fontSize, maxWidth);
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        if (line.length() > 0 || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * Returns the text unchanged if it fits, otherwise its longest prefix that fits with an ellipsis appended.
     *
     * @param text sanitized single-line text
     * @param measurer measures the font in use
     * @param fontSize font size in points
     * @param maxWidth available width in points
     * @return text that fits in {@code maxWidth}
     */
    public static String truncate(String text, TextMeasurer measurer, float fontSize, float maxWidth) {
        if (measurer.width(text, fontSize) <= maxWidth) {
            return text;
        }
        return withEllipsis(text, measurer, fontSize, maxWidth);
    }

    /**
     * Keeps at most {@code maxLines} lines; if lines were dropped, the last kept line ends in an ellipsis.
     *
     * @param lines wrapped lines
     * @param maxLines maximum number of lines, at least one
     * @param measurer measures the font in use
     * @param fontSize font size in points
     * @param maxWidth available width in points
     * @return the limited lines
     */
    public static List<String> limitLines(List<String> lines, int maxLines, TextMeasurer measurer, float fontSize,
            float maxWidth) {
        if (maxLines < 1) {
            throw new IllegalArgumentException("maxLines must be at least 1");
        }
        if (lines.size() <= maxLines) {
            return lines;
        }
        List<String> kept = new ArrayList<>(lines.subList(0, maxLines));
        kept.set(maxLines - 1, withEllipsis(kept.get(maxLines - 1), measurer, fontSize, maxWidth));
        return kept;
    }

    private static String withEllipsis(String text, TextMeasurer measurer, float fontSize, float maxWidth) {
        String base = text.stripTrailing();
        while (!base.isEmpty() && measurer.width(base + ELLIPSIS, fontSize) > maxWidth) {
            base = base.substring(0, previousCodePointStart(base)).stripTrailing();
        }
        return measurer.width(base + ELLIPSIS, fontSize) <= maxWidth ? base + ELLIPSIS : base;
    }

    private static int fittingPrefixLength(String word, TextMeasurer measurer, float fontSize, float maxWidth) {
        int end = 0;
        while (end < word.length()) {
            int next = end + Character.charCount(word.codePointAt(end));
            if (measurer.width(word.substring(0, next), fontSize) > maxWidth) {
                break;
            }
            end = next;
        }
        // Always make progress, even when a single character is wider than the line.
        return end == 0 ? Character.charCount(word.codePointAt(0)) : end;
    }

    private static int previousCodePointStart(String text) {
        return text.offsetByCodePoints(text.length(), -1);
    }
}
