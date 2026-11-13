package com.example.reporting.pdf.a11y;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * Null Object {@link TaggingContext}: draws content untagged and records no structure (AD-12).
 */
public final class NoOpTaggingContext implements TaggingContext {

    private static final Slot SLOT = new Slot() {
    };

    /** Creates a context that does nothing. */
    public NoOpTaggingContext() {
    }

    @Override
    public void beginStructure(Role role) {
    }

    @Override
    public void beginHeaderCell(Scope scope) {
    }

    @Override
    public void beginFigure(String altText) {
    }

    @Override
    public void endStructure() {
    }

    @Override
    public Slot reserveSlot() {
        return SLOT;
    }

    @Override
    public void resumeSlot(Slot slot) {
    }

    @Override
    public void endSlot() {
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

    @Override
    public void finish(PDDocument document) {
    }
}
