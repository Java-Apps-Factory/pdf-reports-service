package com.example.reporting.pdf.layout;

/** Page margins in points. Immutable. */
public final class Margins {

    private final float top;
    private final float right;
    private final float bottom;
    private final float left;

    private Margins(float top, float right, float bottom, float left) {
        this.top = nonNegative(top, "top");
        this.right = nonNegative(right, "right");
        this.bottom = nonNegative(bottom, "bottom");
        this.left = nonNegative(left, "left");
    }

    /**
     * Creates margins.
     *
     * @param top top margin
     * @param right right margin
     * @param bottom bottom margin
     * @param left left margin
     * @return the margins
     */
    public static Margins of(float top, float right, float bottom, float left) {
        return new Margins(top, right, bottom, left);
    }

    /**
     * Creates equal margins on all sides.
     *
     * @param all margin in points
     * @return the margins
     */
    public static Margins uniform(float all) {
        return new Margins(all, all, all, all);
    }

    /**
     * Returns the top margin.
     *
     * @return points
     */
    public float top() {
        return top;
    }

    /**
     * Returns the right margin.
     *
     * @return points
     */
    public float right() {
        return right;
    }

    /**
     * Returns the bottom margin.
     *
     * @return points
     */
    public float bottom() {
        return bottom;
    }

    /**
     * Returns the left margin.
     *
     * @return points
     */
    public float left() {
        return left;
    }

    private static float nonNegative(float value, String name) {
        if (value < 0 || Float.isNaN(value)) {
            throw new IllegalArgumentException(name + " margin must be >= 0");
        }
        return value;
    }
}
