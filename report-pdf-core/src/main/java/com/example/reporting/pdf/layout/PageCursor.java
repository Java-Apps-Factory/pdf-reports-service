package com.example.reporting.pdf.layout;

/**
 * Tracks the current page and the vertical position of the next free line in the body area.
 * Internal to report-pdf-core; mutable and single-threaded.
 *
 * <p>Coordinates are PDF user space: y grows upwards, so advancing moves {@code y} down towards {@code bottom}.
 */
public final class PageCursor {

    /** Tolerance for float comparisons, in points. */
    static final float EPSILON = 0.01f;

    private int pageNumber;
    private float top;
    private float y;
    private float bottom;

    /**
     * Creates a cursor at the top of a page's free body area.
     *
     * @param pageNumber 1-based page number
     * @param top y of the top of the free area
     * @param bottom y of the bottom of the body area
     */
    public PageCursor(int pageNumber, float top, float bottom) {
        startPage(pageNumber, top, bottom);
    }

    /**
     * Moves the cursor to the top of a new page.
     *
     * @param newPageNumber 1-based page number
     * @param newTop y of the top of the free area
     * @param newBottom y of the bottom of the body area
     */
    public void startPage(int newPageNumber, float newTop, float newBottom) {
        if (newPageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be >= 1");
        }
        if (newTop < newBottom) {
            throw new IllegalArgumentException("top must not be below bottom");
        }
        this.pageNumber = newPageNumber;
        this.top = newTop;
        this.y = newTop;
        this.bottom = newBottom;
    }

    /**
     * Returns the current 1-based page number.
     *
     * @return page number
     */
    public int pageNumber() {
        return pageNumber;
    }

    /**
     * Returns the y of the next free line.
     *
     * @return points from the page's bottom edge
     */
    public float y() {
        return y;
    }

    /**
     * Returns the free height left on this page.
     *
     * @return points
     */
    public float remaining() {
        return y - bottom;
    }

    /**
     * Tells whether a block of the given height fits below the cursor.
     *
     * @param height block height in points
     * @return true if it fits
     */
    public boolean fits(float height) {
        return height <= remaining() + EPSILON;
    }

    /**
     * Tells whether nothing has been placed on this page yet.
     *
     * @return true at the top of the free area
     */
    public boolean isAtTop() {
        return Math.abs(top - y) < EPSILON;
    }

    /**
     * Moves the cursor down.
     *
     * @param height points to advance; must fit
     */
    public void advance(float height) {
        if (height < 0) {
            throw new IllegalArgumentException("height must be >= 0");
        }
        if (!fits(height)) {
            throw new IllegalStateException("Block of " + height + " pt does not fit; " + remaining() + " pt left");
        }
        y -= height;
    }
}
