package com.example.reporting.pdf.decorate;

import com.example.reporting.pdf.a11y.TaggingContext.Slot;
import java.util.Objects;

/**
 * A section heading placed in pass 1 and drawn in pass 2, once its row count is known. Internal to
 * report-pdf-core.
 */
public final class SectionHeading {

    private final int pageNumber;
    private final float top;
    private final String text;
    private final Slot slot;

    /**
     * Creates a placed heading.
     *
     * @param pageNumber 1-based page the heading is on
     * @param top y of the top of the heading line
     * @param text resolved heading text (unsanitized)
     * @param slot structure position of the heading, reserved in pass 1 before the section's table
     */
    public SectionHeading(int pageNumber, float top, String text, Slot slot) {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be >= 1");
        }
        this.pageNumber = pageNumber;
        this.top = top;
        this.text = Objects.requireNonNull(text, "text");
        this.slot = Objects.requireNonNull(slot, "slot");
    }

    /**
     * Returns the structure slot of the heading.
     *
     * @return slot
     */
    public Slot slot() {
        return slot;
    }

    /**
     * Returns the 1-based page number.
     *
     * @return page number
     */
    public int pageNumber() {
        return pageNumber;
    }

    /**
     * Returns the y of the top of the heading line.
     *
     * @return points from the page's bottom edge
     */
    public float top() {
        return top;
    }

    /**
     * Returns the heading text.
     *
     * @return text
     */
    public String text() {
        return text;
    }
}
