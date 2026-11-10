package com.example.reporting.pdf;

import java.util.ArrayList;
import java.util.List;

/** Outcome of {@link ReportDocumentWriter#finish}: page count, rows written and each section's first page. */
public final class RenderResult {

    private final int pageCount;
    private final long rowsWritten;
    private final List<Integer> sectionFirstPages;

    private RenderResult(Builder builder) {
        if (builder.pageCount < 1) {
            throw new IllegalArgumentException("pageCount must be >= 1");
        }
        if (builder.rowsWritten < 0) {
            throw new IllegalArgumentException("rowsWritten must be >= 0");
        }
        this.pageCount = builder.pageCount;
        this.rowsWritten = builder.rowsWritten;
        this.sectionFirstPages = List.copyOf(builder.sectionFirstPages);
    }

    /**
     * Returns a new builder.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the number of pages.
     *
     * @return at least 1
     */
    public int pageCount() {
        return pageCount;
    }

    /**
     * Returns the number of data rows written over all sections.
     *
     * @return row count
     */
    public long rowsWritten() {
        return rowsWritten;
    }

    /**
     * Returns the 1-based page of each section's heading, in section order.
     *
     * @return unmodifiable list
     */
    public List<Integer> sectionFirstPages() {
        return sectionFirstPages;
    }

    /** Builder for {@link RenderResult}. */
    public static final class Builder {
        private int pageCount;
        private long rowsWritten;
        private final List<Integer> sectionFirstPages = new ArrayList<>();

        private Builder() {
        }

        /**
         * Sets the page count.
         *
         * @param value pages
         * @return this builder
         */
        public Builder pageCount(int value) {
            this.pageCount = value;
            return this;
        }

        /**
         * Sets the rows written.
         *
         * @param value rows
         * @return this builder
         */
        public Builder rowsWritten(long value) {
            this.rowsWritten = value;
            return this;
        }

        /**
         * Sets the first page of each section.
         *
         * @param pages 1-based pages in section order
         * @return this builder
         */
        public Builder sectionFirstPages(List<Integer> pages) {
            this.sectionFirstPages.clear();
            this.sectionFirstPages.addAll(pages);
            return this;
        }

        /**
         * Builds the result.
         *
         * @return immutable result
         */
        public RenderResult build() {
            return new RenderResult(this);
        }
    }
}
