package com.example.reporting.pdf;

import com.example.reporting.pdf.a11y.NoOpTaggingContext;
import com.example.reporting.pdf.a11y.PdfUaTaggingContext;
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
     * does not exist; the temp files are deleted when the writer is closed. With tagging on, the output is
     * PDF/UA-1 and PDF/A-2a (SPEC section 12).
     *
     * @param spec the document-level layout and content
     * @param workDir directory for temp files
     * @return a new single-use writer
     * @throws IOException if the directory or the fonts cannot be set up
     */
    public static ReportDocumentWriter open(DocumentSpec spec, Path workDir) throws IOException {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(workDir, "workDir");
        Files.createDirectories(workDir);
        TaggingContext tagging = spec.taggingEnabled() ? new PdfUaTaggingContext() : new NoOpTaggingContext();
        return new StreamingReportDocumentWriter(spec, workDir, tagging);
    }
}
