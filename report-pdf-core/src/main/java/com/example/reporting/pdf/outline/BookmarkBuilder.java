package com.example.reporting.pdf.outline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PageMode;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

/**
 * Builds the document outline: "Summary" first, then one bookmark per category section pointing to the
 * heading on its first page (SPEC section 10). Internal to report-pdf-core.
 */
public final class BookmarkBuilder {

    private final List<Entry> entries = new ArrayList<>();

    /** Creates an empty builder. */
    public BookmarkBuilder() {
    }

    /**
     * Adds a bookmark.
     *
     * @param title bookmark text
     * @param pageNumber 1-based target page
     * @param top y on the target page to scroll to
     * @return this builder
     */
    public BookmarkBuilder add(String title, int pageNumber, float top) {
        entries.add(new Entry(Objects.requireNonNull(title, "title"), pageNumber, top));
        return this;
    }

    /**
     * Writes the outline into the document and makes viewers open with it visible.
     *
     * @param document the document
     */
    public void build(PDDocument document) {
        PDDocumentOutline outline = new PDDocumentOutline();
        for (Entry entry : entries) {
            PDPageXYZDestination destination = new PDPageXYZDestination();
            destination.setPage(document.getPage(entry.pageNumber - 1));
            destination.setTop(Math.round(entry.top));
            PDOutlineItem item = new PDOutlineItem();
            item.setTitle(entry.title);
            item.setDestination(destination);
            outline.addLast(item);
        }
        outline.openNode();
        document.getDocumentCatalog().setDocumentOutline(outline);
        document.getDocumentCatalog().setPageMode(PageMode.USE_OUTLINES);
    }

    private static final class Entry {
        private final String title;
        private final int pageNumber;
        private final float top;

        private Entry(String title, int pageNumber, float top) {
            this.title = title;
            this.pageNumber = pageNumber;
            this.top = top;
        }
    }
}
