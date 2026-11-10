package com.example.reporting.pdf.layout;

import java.util.Objects;

/**
 * Page size, margins and the header and footer regions; derives the body area of each page.
 *
 * <p>Body area = page height − margins − header region − footer region. The header region is the title block
 * on page 1 and the running header on pages 2+ (SPEC section 10). Defaults are US Letter landscape with
 * 36 pt margins (SPEC section 11).
 */
public final class PageGeometry {

    private final float width;
    private final float height;
    private final Margins margins;
    private final float titleBlockHeight;
    private final float runningHeaderHeight;
    private final float footerHeight;

    private PageGeometry(Builder builder) {
        this.width = positive(builder.width, "width");
        this.height = positive(builder.height, "height");
        this.margins = Objects.requireNonNull(builder.margins, "margins");
        this.titleBlockHeight = positive(builder.titleBlockHeight, "titleBlockHeight");
        this.runningHeaderHeight = positive(builder.runningHeaderHeight, "runningHeaderHeight");
        this.footerHeight = positive(builder.footerHeight, "footerHeight");
        if (bodyHeight(1) <= 0 || bodyHeight(2) <= 0 || contentWidth() <= 0) {
            throw new IllegalArgumentException("Margins, header and footer leave no body area");
        }
    }

    /**
     * Returns a builder preset to US Letter landscape (792 × 612 pt), 36 pt margins, 96 pt title block,
     * 28 pt running header and 28 pt footer.
     *
     * @return a builder with the SPEC section 11 defaults
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the default geometry from SPEC section 11.
     *
     * @return US Letter landscape geometry
     */
    public static PageGeometry letterLandscape() {
        return builder().build();
    }

    /**
     * Returns the page width.
     *
     * @return points
     */
    public float width() {
        return width;
    }

    /**
     * Returns the page height.
     *
     * @return points
     */
    public float height() {
        return height;
    }

    /**
     * Returns the margins.
     *
     * @return margins
     */
    public Margins margins() {
        return margins;
    }

    /**
     * Returns the title block height (page 1 header region).
     *
     * @return points
     */
    public float titleBlockHeight() {
        return titleBlockHeight;
    }

    /**
     * Returns the running header height (pages 2+ header region).
     *
     * @return points
     */
    public float runningHeaderHeight() {
        return runningHeaderHeight;
    }

    /**
     * Returns the footer height.
     *
     * @return points
     */
    public float footerHeight() {
        return footerHeight;
    }

    /**
     * Returns the x coordinate of the left content edge.
     *
     * @return points from the page's left edge
     */
    public float contentLeft() {
        return margins.left();
    }

    /**
     * Returns the width between the left and right margins.
     *
     * @return points
     */
    public float contentWidth() {
        return width - margins.left() - margins.right();
    }

    /**
     * Returns the y coordinate of the top of the header region.
     *
     * @return points from the page's bottom edge
     */
    public float headerTop() {
        return height - margins.top();
    }

    /**
     * Returns the header region height of a page.
     *
     * @param pageNumber 1-based page number
     * @return title block height on page 1, running header height otherwise
     */
    public float headerHeight(int pageNumber) {
        return pageNumber == 1 ? titleBlockHeight : runningHeaderHeight;
    }

    /**
     * Returns the y coordinate of the top of the body area.
     *
     * @param pageNumber 1-based page number
     * @return points from the page's bottom edge
     */
    public float bodyTop(int pageNumber) {
        return headerTop() - headerHeight(pageNumber);
    }

    /**
     * Returns the y coordinate of the bottom of the body area, which is also the top of the footer region.
     *
     * @return points from the page's bottom edge
     */
    public float bodyBottom() {
        return margins.bottom() + footerHeight;
    }

    /**
     * Returns the body height of a page.
     *
     * @param pageNumber 1-based page number
     * @return page height − margins − header region − footer region
     */
    public float bodyHeight(int pageNumber) {
        return bodyTop(pageNumber) - bodyBottom();
    }

    private static float positive(float value, String name) {
        if (!(value > 0)) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    /** Builder for {@link PageGeometry}. */
    public static final class Builder {
        private float width = 792f;
        private float height = 612f;
        private Margins margins = Margins.uniform(36f);
        private float titleBlockHeight = 96f;
        private float runningHeaderHeight = 28f;
        private float footerHeight = 28f;

        private Builder() {
        }

        /**
         * Sets the page size.
         *
         * @param pageWidth width in points
         * @param pageHeight height in points
         * @return this builder
         */
        public Builder pageSize(float pageWidth, float pageHeight) {
            this.width = pageWidth;
            this.height = pageHeight;
            return this;
        }

        /**
         * Sets the margins.
         *
         * @param value margins
         * @return this builder
         */
        public Builder margins(Margins value) {
            this.margins = value;
            return this;
        }

        /**
         * Sets the title block height.
         *
         * @param value points
         * @return this builder
         */
        public Builder titleBlockHeight(float value) {
            this.titleBlockHeight = value;
            return this;
        }

        /**
         * Sets the running header height.
         *
         * @param value points
         * @return this builder
         */
        public Builder runningHeaderHeight(float value) {
            this.runningHeaderHeight = value;
            return this;
        }

        /**
         * Sets the footer height.
         *
         * @param value points
         * @return this builder
         */
        public Builder footerHeight(float value) {
            this.footerHeight = value;
            return this;
        }

        /**
         * Builds the geometry.
         *
         * @return immutable geometry
         */
        public PageGeometry build() {
            return new PageGeometry(this);
        }
    }
}
