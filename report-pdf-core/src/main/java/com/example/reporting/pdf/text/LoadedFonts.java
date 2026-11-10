package com.example.reporting.pdf.text;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * The regular, bold and italic faces loaded into one document with subset embedding. Internal to
 * report-pdf-core.
 */
public final class LoadedFonts {

    private final FontFace regular;
    private final FontFace bold;
    private final FontFace italic;

    private LoadedFonts(FontFace regular, FontFace bold, FontFace italic) {
        this.regular = regular;
        this.bold = bold;
        this.italic = italic;
    }

    /**
     * Loads the font set into {@code document} with {@code PDType0Font.load(..., true)} (subset embedding).
     *
     * @param document the document being written
     * @param fonts the shared font bytes
     * @return document-local faces
     * @throws IOException if a font cannot be parsed
     */
    public static LoadedFonts load(PDDocument document, FontSet fonts) throws IOException {
        return new LoadedFonts(
                face(document, fonts.regular()),
                face(document, fonts.bold()),
                face(document, fonts.italic()));
    }

    private static FontFace face(PDDocument document, byte[] bytes) throws IOException {
        return new FontFace(PDType0Font.load(document, new ByteArrayInputStream(bytes), true));
    }

    /**
     * Returns the regular face.
     *
     * @return regular face
     */
    public FontFace regular() {
        return regular;
    }

    /**
     * Returns the bold face.
     *
     * @return bold face
     */
    public FontFace bold() {
        return bold;
    }

    /**
     * Returns the italic face.
     *
     * @return italic face
     */
    public FontFace italic() {
        return italic;
    }
}
