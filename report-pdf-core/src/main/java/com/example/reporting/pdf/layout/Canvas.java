package com.example.reporting.pdf.layout;

import java.io.IOException;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Thin drawing helper over one page content stream: text, rules, fills and images in gray levels.
 * Internal to report-pdf-core; does not own or close the stream.
 */
public final class Canvas {

    private final PDPageContentStream stream;

    /**
     * Wraps a content stream.
     *
     * @param stream an open page content stream
     */
    public Canvas(PDPageContentStream stream) {
        this.stream = Objects.requireNonNull(stream, "stream");
    }

    /**
     * Returns the wrapped stream, for marked-content operators.
     *
     * @return the content stream
     */
    public PDPageContentStream stream() {
        return stream;
    }

    /**
     * Draws one line of sanitized text in black.
     *
     * @param font document-local font
     * @param size font size
     * @param x left edge of the text
     * @param baseline baseline y
     * @param text sanitized text without line breaks
     * @throws IOException on a stream error
     */
    public void text(PDFont font, float size, float x, float baseline, String text) throws IOException {
        if (text.isEmpty()) {
            return;
        }
        stream.setNonStrokingColor(LayoutMetrics.TEXT_GRAY, LayoutMetrics.TEXT_GRAY, LayoutMetrics.TEXT_GRAY);
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, baseline);
        stream.showText(text);
        stream.endText();
    }

    /**
     * Draws a straight rule.
     *
     * @param x1 start x
     * @param y1 start y
     * @param x2 end x
     * @param y2 end y
     * @param width line width
     * @param gray gray level 0 (black) to 1 (white)
     * @throws IOException on a stream error
     */
    public void line(float x1, float y1, float x2, float y2, float width, float gray) throws IOException {
        stream.setStrokingColor(gray, gray, gray);
        stream.setLineWidth(width);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
    }

    /**
     * Fills a rectangle.
     *
     * @param x left
     * @param y bottom
     * @param width width
     * @param height height
     * @param gray gray level 0 (black) to 1 (white)
     * @throws IOException on a stream error
     */
    public void fillRect(float x, float y, float width, float height, float gray) throws IOException {
        stream.setNonStrokingColor(gray, gray, gray);
        stream.addRect(x, y, width, height);
        stream.fill();
    }

    /**
     * Strokes a rectangle outline.
     *
     * @param x left
     * @param y bottom
     * @param width width
     * @param height height
     * @param lineWidth line width
     * @param gray gray level 0 (black) to 1 (white)
     * @throws IOException on a stream error
     */
    public void strokeRect(float x, float y, float width, float height, float lineWidth, float gray)
            throws IOException {
        stream.setStrokingColor(gray, gray, gray);
        stream.setLineWidth(lineWidth);
        stream.addRect(x, y, width, height);
        stream.stroke();
    }

    /**
     * Draws an image.
     *
     * @param image document-local image
     * @param x left
     * @param y bottom
     * @param width drawn width
     * @param height drawn height
     * @throws IOException on a stream error
     */
    public void image(PDImageXObject image, float x, float y, float width, float height) throws IOException {
        stream.drawImage(image, x, y, width, height);
    }
}
