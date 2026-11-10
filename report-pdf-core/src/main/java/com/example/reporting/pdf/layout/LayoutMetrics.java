package com.example.reporting.pdf.layout;

/**
 * Font sizes, spacing and fills of the single report template (SPEC section 11). Internal to report-pdf-core.
 *
 * <p>Page-level dimensions live in {@link PageGeometry}; these values are fixed by the template.
 */
public final class LayoutMetrics {

    /** Title line size. */
    public static final float TITLE_SIZE = 16f;
    /** Subtitle line size. */
    public static final float SUBTITLE_SIZE = 11f;
    /** FIPS line size. */
    public static final float FIPS_LINE_SIZE = 12f;
    /** Size of the other title block lines. */
    public static final float INFO_LINE_SIZE = 9f;
    /** Rule under the title block. */
    public static final float TITLE_RULE_WIDTH = 0.75f;
    /** Maximum logo height. */
    public static final float LOGO_MAX_HEIGHT = 54f;
    /** Maximum logo width. */
    public static final float LOGO_MAX_WIDTH = 144f;
    /** Gap between the logo column and the title text. */
    public static final float LOGO_GAP = 12f;
    /** Line spacing factor for single title block and header lines. */
    public static final float LINE_SPACING = 1.25f;

    /** Running header text size. */
    public static final float RUNNING_HEADER_SIZE = 9f;
    /** Footer disclaimer size. */
    public static final float DISCLAIMER_SIZE = 8f;
    /** Footer report ID and page number size. */
    public static final float FOOTER_SIZE = 9f;
    /** Header and footer rules. */
    public static final float THIN_RULE_WIDTH = 0.5f;

    /** Section and summary heading size. */
    public static final float HEADING_SIZE = 12f;
    /** Heading line height. */
    public static final float HEADING_LEADING = 15f;
    /** Space above a section heading (omitted at the top of a page). */
    public static final float HEADING_SPACE_ABOVE = 12f;
    /** Space below a heading. */
    public static final float HEADING_SPACE_BELOW = 6f;

    /** Continuation label size. */
    public static final float CONTINUATION_SIZE = 10f;
    /** Continuation label line height. */
    public static final float CONTINUATION_LEADING = 13f;
    /** Space below the continuation label. */
    public static final float CONTINUATION_SPACE_BELOW = 4f;

    /** Table text size. */
    public static final float TABLE_SIZE = 9f;
    /** Table line height. */
    public static final float TABLE_LEADING = 11f;
    /** Horizontal cell padding. */
    public static final float CELL_PADDING_X = 3f;
    /** Vertical cell padding. */
    public static final float CELL_PADDING_Y = 2f;
    /** Cell border width. */
    public static final float BORDER_WIDTH = 0.5f;
    /** Header row fill, #E6E6E6. */
    public static final float HEADER_FILL_GRAY = 0xE6 / 255f;
    /** Even data row fill, #F5F5F5. */
    public static final float EVEN_ROW_FILL_GRAY = 0xF5 / 255f;
    /** Border colour, #808080. */
    public static final float BORDER_GRAY = 0x80 / 255f;
    /** Text colour, black. */
    public static final float TEXT_GRAY = 0f;

    /** Empty-category message size. */
    public static final float EMPTY_MESSAGE_SIZE = 9f;
    /** Space above the empty-category message. */
    public static final float EMPTY_MESSAGE_SPACE_ABOVE = 4f;

    /** Summary table: Category column width. */
    public static final float SUMMARY_LABEL_WIDTH = 260f;
    /** Summary table: Records column width. */
    public static final float SUMMARY_COUNT_WIDTH = 100f;

    private LayoutMetrics() {
    }

    /**
     * Returns the height of a table row with the given number of text lines.
     *
     * @param lines line count, at least 1
     * @return lines × leading + vertical padding
     */
    public static float rowHeight(int lines) {
        return lines * TABLE_LEADING + 2 * CELL_PADDING_Y;
    }

    /**
     * Returns the height of the summary area reserved on page 1.
     *
     * @param summaryRows number of category rows
     * @return heading + (rows + 2) × row height (header row and total row)
     */
    public static float summaryHeight(int summaryRows) {
        return HEADING_LEADING + HEADING_SPACE_BELOW + (summaryRows + 2) * rowHeight(1);
    }

    /**
     * Returns the height of a section heading block.
     *
     * @param atPageTop true when the heading is the first thing on the page
     * @return heading height with spacing
     */
    public static float headingBlockHeight(boolean atPageTop) {
        return (atPageTop ? 0f : HEADING_SPACE_ABOVE) + HEADING_LEADING + HEADING_SPACE_BELOW;
    }

    /**
     * Returns the height of the continuation label block.
     *
     * @return label height with spacing
     */
    public static float continuationHeight() {
        return CONTINUATION_LEADING + CONTINUATION_SPACE_BELOW;
    }

    /**
     * Returns the height of the empty-category message block.
     *
     * @return message height with spacing
     */
    public static float emptyMessageHeight() {
        return EMPTY_MESSAGE_SPACE_ABOVE + TABLE_LEADING;
    }

    /**
     * Returns the baseline of a text line inside a block that starts at {@code top}.
     *
     * @param top y of the top of the line box
     * @param leading line height
     * @param fontSize font size
     * @return baseline y
     */
    public static float baseline(float top, float leading, float fontSize) {
        // Centre the glyph box in the line, then drop by the typical descender (about 0.22 em).
        return top - leading + (leading - fontSize) / 2f + 0.22f * fontSize;
    }
}
