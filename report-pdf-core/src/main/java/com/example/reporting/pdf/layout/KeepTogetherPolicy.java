package com.example.reporting.pdf.layout;

/**
 * Keep-together rule for section starts (SPEC section 10): a section heading must fit together with the table
 * header row and the first data row (or the empty message); otherwise the section starts on a new page.
 * Internal to report-pdf-core.
 */
public final class KeepTogetherPolicy {

    private KeepTogetherPolicy() {
    }

    /**
     * Decides whether a section must start on a new page.
     *
     * <p>A section already at the top of a page never moves: a fresh page could not give it more room.
     *
     * @param cursor the current position
     * @param headingHeight height of the heading block including its spacing
     * @param headerRowHeight height of the table header row
     * @param firstContentHeight height of the first data row or empty message; 0 when there is none
     * @return true if the section must start on a new page
     */
    public static boolean startsOnNewPage(PageCursor cursor, float headingHeight, float headerRowHeight,
            float firstContentHeight) {
        if (cursor.isAtTop()) {
            return false;
        }
        return !cursor.fits(headingHeight + headerRowHeight + firstContentHeight);
    }
}
