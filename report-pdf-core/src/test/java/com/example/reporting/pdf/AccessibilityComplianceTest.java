package com.example.reporting.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.verapdf.pdfa.flavours.PDFAFlavour;

/**
 * Phase 5 acceptance (SPEC sections 12 and 21): veraPDF reports zero PDF/UA-1 and zero PDF/A-2a failures on the
 * three sample reports (empty category, multi-page category, single row). The PDFs are also written to
 * {@code target/pdfua-*.pdf} for manual checks with PAC and a screen reader.
 */
class AccessibilityComplianceTest {

    @TempDir
    Path workDir;

    static Stream<Arguments> samples() {
        return Stream.of("empty-category", "multi-page-category", "single-row")
                .flatMap(sample -> Stream.of(Arguments.of(sample, PDFAFlavour.PDFUA_1),
                        Arguments.of(sample, PDFAFlavour.PDFA_2_A)));
    }

    @ParameterizedTest(name = "{0} is {1}")
    @MethodSource("samples")
    void samplePassesVeraPdf(String sample, PDFAFlavour flavour) throws Exception {
        byte[] pdf = render(sample, workDir);
        assertThat(PdfCompliance.failures(pdf, flavour)).as("veraPDF %s failures for %s", flavour, sample)
                .isEmpty();
    }

    static byte[] render(String sample, Path workDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ReportDocumentWriter writer = ReportDocumentWriters.open(
                TestReports.spec(2).taggingEnabled(true).build(), workDir)) {
            switch (sample) {
                case "empty-category":
                    writer.beginSection(TestReports.section(1, "Fees"));
                    writer.addRow(TestReports.row(1));
                    writer.endSection();
                    writer.beginSection(TestReports.section(2, "Inspections"));
                    writer.addEmptyMessage("No content to display in this category.");
                    writer.endSection();
                    break;
                case "multi-page-category":
                    writer.beginSection(TestReports.section(1, "Fees"));
                    for (int i = 0; i < 120; i++) {
                        writer.addRow(TestReports.row(i));
                    }
                    writer.endSection();
                    writer.beginSection(TestReports.section(2, "Inspections"));
                    writer.addRow(TestReports.row(500));
                    writer.endSection();
                    break;
                default:
                    writer.beginSection(TestReports.section(1, "Fees"));
                    writer.addRow(TestReports.row(7));
                    writer.endSection();
                    writer.beginSection(TestReports.section(2, "Inspections"));
                    writer.addEmptyMessage("No content to display in this category.");
                    writer.endSection();
                    break;
            }
            long fees = "multi-page-category".equals(sample) ? 120 : 1;
            long inspections = "multi-page-category".equals(sample) ? 1 : 0;
            writer.finish(SummaryData.builder().addRow("Category 1 – Fees", fees)
                    .addRow("Category 2 – Inspections", inspections).build(), out);
        }
        Path target = Paths.get("target");
        Files.createDirectories(target);
        Files.write(target.resolve("pdfua-" + sample + ".pdf"), out.toByteArray());
        return out.toByteArray();
    }
}
