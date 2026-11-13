package com.example.reporting.pdf.decorate;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Loads the logo image. An image with an alpha channel is flattened onto white first, so the PDF never gets a
 * soft mask or transparency group for it (SPEC section 12). Internal to report-pdf-core.
 */
final class LogoImages {

    private LogoImages() {
    }

    static PDImageXObject load(PDDocument document, byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || !image.getColorModel().hasAlpha()) {
            return PDImageXObject.createFromByteArray(document, bytes, "logo");
        }
        BufferedImage flat = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flat.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, flat.getWidth(), flat.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return LosslessFactory.createFromImage(document, flat);
    }
}
