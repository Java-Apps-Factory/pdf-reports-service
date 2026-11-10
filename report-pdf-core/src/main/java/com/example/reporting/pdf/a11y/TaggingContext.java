package com.example.reporting.pdf.a11y;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * Accessibility tagging hooks called by the layout code (AD-12).
 *
 * <p>Layout code opens and closes structure elements around what it draws, wraps real content in
 * {@link #markContent} and wraps decoration in {@link #beginArtifact}/{@link #endArtifact}. Without tagging
 * the {@link NoOpTaggingContext} is used; the PDF/UA implementation builds the structure tree of SPEC
 * section 12. A context belongs to one document and one thread.
 */
public interface TaggingContext {

    /** Structure element types used by the report (SPEC section 12). */
    enum Role {
        /** Document root. */
        DOCUMENT,
        /** Report title. */
        H1,
        /** Paragraph: title block lines, empty-category message. */
        P,
        /** Logo. */
        FIGURE,
        /** Summary or category section. */
        SECT,
        /** Section heading. */
        H2,
        /** Table. */
        TABLE,
        /** Table head. */
        THEAD,
        /** Table body. */
        TBODY,
        /** Table row. */
        TR,
        /** Header cell. */
        TH,
        /** Data cell. */
        TD
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

    /**
     * Opens a structure element as a child of the currently open one.
     *
     * @param role element type
     */
    void beginStructure(Role role);

    /**
     * Opens a {@link Role#FIGURE} element with alternate text.
     *
     * @param altText alternate text read by assistive technology
     */
    void beginFigure(String altText);

    /** Closes the most recently opened structure element. */
    void endStructure();

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
}
