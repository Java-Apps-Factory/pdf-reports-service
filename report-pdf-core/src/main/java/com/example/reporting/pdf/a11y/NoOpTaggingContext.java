package com.example.reporting.pdf.a11y;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * Null Object {@link TaggingContext}: draws content untagged and records no structure (AD-12).
 */
public final class NoOpTaggingContext implements TaggingContext {

    /** Creates a context that does nothing. */
    public NoOpTaggingContext() {
    }

    @Override
    public void beginStructure(Role role) {
    }

    @Override
    public void beginFigure(String altText) {
    }

    @Override
    public void endStructure() {
    }

    @Override
    public void markContent(PDPageContentStream stream, PDPage page, Drawing drawing) throws IOException {
        drawing.draw();
    }

    @Override
    public void beginArtifact(PDPageContentStream stream, ArtifactType type) {
    }

    @Override
    public void endArtifact(PDPageContentStream stream) {
    }
}
