package com.example.reporting.pdf;

import com.example.reporting.pdf.a11y.NoOpTaggingContext;
import com.example.reporting.pdf.a11y.TaggingContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Factory for {@link ReportDocumentWriter} instances. */
public final class ReportDocumentWriters {

    private ReportDocumentWriters() {
    }

    /**
     * Opens a new writer. Page content is buffered in temp files under {@code workDir}, which is created if it
     * does not exist; the temp files are deleted when the writer is closed.
     *
     * @param spec the document-level layout and content
     * @param workDir directory for temp files
     * @return a new single-use writer
     * @throws IOException if the directory or the fonts cannot be set up
     * @throws UnsupportedOperationException if tagging is requested; PDF/UA tagging arrives in Phase 5
     */
    public static ReportDocumentWriter open(DocumentSpec spec, Path workDir) throws IOException {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(workDir, "workDir");
        if (spec.taggingEnabled()) {
            throw new UnsupportedOperationException("PDF/UA tagging is not implemented yet (SPEC Phase 5)");
        }
        Files.createDirectories(workDir);
        TaggingContext tagging = new NoOpTaggingContext();
        return new StreamingReportDocumentWriter(spec, workDir, tagging);
    }
}
