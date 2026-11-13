package com.example.reporting.pdf.a11y;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * Accessibility tagging hooks called by the layout code (AD-12).
 *
 * <p>Layout code opens and closes structure elements around what it draws, wraps real content in
 * {@link #markContent} and wraps decoration in {@link #beginArtifact}/{@link #endArtifact}. The order in which
 * elements are opened is the reading order. Content drawn in pass 2 (title block, summary, section headings)
 * is placed in reading order through a {@link Slot} reserved in pass 1 and resumed in pass 2.
 *
 * <p>{@link NoOpTaggingContext} does nothing; {@link PdfUaTaggingContext} builds the PDF/UA structure tree of
 * SPEC section 12. A context belongs to one document and one thread.
 */
public interface TaggingContext {

    /** Structure element types used by the report (SPEC section 12); all are standard PDF types. */
    enum Role {
        /** Document root. */
        DOCUMENT("Document"),
        /** Report title. */
        H1("H1"),
        /** Paragraph: title block lines, empty-category message. */
        P("P"),
        /** Logo. */
        FIGURE("Figure"),
        /** Summary or category section. */
        SECT("Sect"),
        /** Section heading. */
        H2("H2"),
        /** Table. */
        TABLE("Table"),
        /** Table head. */
        THEAD("THead"),
        /** Table body. */
        TBODY("TBody"),
        /** Table row. */
        TR("TR"),
        /** Header cell. */
        TH("TH"),
        /** Data cell. */
        TD("TD");

        private final String pdfName;

        Role(String pdfName) {
            this.pdfName = pdfName;
        }

        /**
         * Returns the standard structure type name.
         *
         * @return PDF structure type, e.g. {@code TD}
         */
        public String pdfName() {
            return pdfName;
        }
    }

    /** Scope of a header cell. */
    enum Scope {
        /** Header of its column. */
        COLUMN,
        /** Header of its row. */
        ROW
    }

    /** Artifact subtypes. */
    enum ArtifactType {
        /** Running headers, footers, page numbers, continuation labels, repeated header rows. */
        PAGINATION,
        /** Rules, fills and borders. */
        LAYOUT
    }

    /** Drawing operations to run inside a marked-content sequence. */
    @FunctionalInterface
    interface Drawing {
        /**
         * Draws onto the current content stream.
         *
         * @throws IOException on a stream error
         */
        void draw() throws IOException;
    }

    /** A position in the structure tree reserved in pass 1 and filled in pass 2. */
    interface Slot {
    }

    /**
     * Opens a structure element as a child of the currently open one (or at the resumed slot).
     *
     * @param role element type
     */
    void beginStructure(Role role);

    /**
     * Opens a {@link Role#TH} element with a scope attribute.
     *
     * @param scope column or row
     */
    void beginHeaderCell(Scope scope);

    /**
     * Opens a {@link Role#FIGURE} element with alternate text.
     *
     * @param altText alternate text read by assistive technology
     */
    void beginFigure(String altText);

    /** Closes the most recently opened structure element. */
    void endStructure();

    /**
     * Reserves the current position among the children of the open element.
     *
     * @return the slot
     */
    Slot reserveSlot();

    /**
     * Makes the following {@link #beginStructure} calls insert their elements at the slot, in call order,
     * until {@link #endSlot()}.
     *
     * @param slot a slot of this context
     */
    void resumeSlot(Slot slot);

    /** Ends the most recent {@link #resumeSlot}. */
    void endSlot();

    /**
     * Runs {@code drawing} as marked content belonging to the currently open structure element.
     *
     * @param stream the page content stream
     * @param page the page drawn on
     * @param drawing the drawing operations
     * @throws IOException on a stream error
     */
    void markContent(PDPageContentStream stream, PDPage page, Drawing drawing) throws IOException;

    /**
     * Starts an artifact sequence; content up to {@link #endArtifact} is never read aloud.
     *
     * @param stream the page content stream
     * @param type artifact subtype
     * @throws IOException on a stream error
     */
    void beginArtifact(PDPageContentStream stream, ArtifactType type) throws IOException;

    /**
     * Ends the artifact sequence started by {@link #beginArtifact}.
     *
     * @param stream the page content stream
     * @throws IOException on a stream error
     */
    void endArtifact(PDPageContentStream stream) throws IOException;

    /**
     * Writes the finished structure tree into the document; called once after pass 2, before saving.
     *
     * @param document the document
     */
    void finish(PDDocument document);
}
