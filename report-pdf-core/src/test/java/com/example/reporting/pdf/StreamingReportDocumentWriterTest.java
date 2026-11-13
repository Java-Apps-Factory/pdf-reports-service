package com.example.reporting.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reporting.pdf.layout.LayoutMetrics;
import com.example.reporting.pdf.layout.PageGeometry;
import com.example.reporting.pdf.table.CellValue;
import com.example.reporting.pdf.table.ColumnSpec;
import com.example.reporting.pdf.table.TableSpec;
import com.example.reporting.pdf.text.TextWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pass 1 layout rules of SPEC section 10, checked on real PDFs. */
class StreamingReportDocumentWriterTest {

    private static final TableSpec NARROW = TableSpec.builder()
            .addColumn(ColumnSpec.builder("Key", 30).wrap(false).build())
            .addColumn(ColumnSpec.builder("Text", 70).build())
            .build();

    @TempDir
    Path workDir;

    @Test
    void pageContentLivesInTempFilesThatCloseDeletes() throws IOException {
        Path dir = workDir.resolve("scratch");
        ReportDocumentWriter writer = ReportDocumentWriters.open(TestReports.spec(1).build(), dir);
        writer.beginSection(section("A"));
        for (int i = 0; i < 300; i++) {
            writer.addRow(row("k" + i, "value " + i));
        }
        assertThat(fileCount(dir)).as("temp files while writing").isPositive();
        writer.endSection();
        writer.finish(summary(1, 300), new ByteArrayOutputStream());
        writer.close();
        assertThat(fileCount(dir)).as("temp files after close").isZero();
        writer.close(); // safe twice
    }

    @Test
    void fontsAreEmbeddedAsSubsets() throws IOException {
        try (PdfPages pdf = render(1, writer -> {
            writer.beginSection(section("A"));
            writer.addRow(row("k", "v"));
            writer.endSection();
        }, summary(1, 1))) {
            PDPage page = pdf.document().getPage(0);
            List<String> names = new ArrayList<>();
            for (COSName name : page.getResources().getFontNames()) {
                PDFont font = page.getResources().getFont(name);
                names.add(font.getName());
                assertThat(font.isEmbedded()).isTrue();
            }
            assertThat(names).isNotEmpty().allMatch(name -> name.matches("[A-Z]{6}\\+NotoSans.*"));
        }
    }

    @Test
    void firstSectionStartsBelowTheReservedSummaryArea() throws IOException {
        int summaryRows = 5;
        try (PdfPages pdf = render(summaryRows, writer -> {
            writer.beginSection(section("A"));
            writer.addRow(row("k", "v"));
            writer.endSection();
        }, summary(1, 1))) {
            float expectedTop = PageGeometry.letterLandscape().bodyTop(1) - LayoutMetrics.summaryHeight(summaryRows)
                    - LayoutMetrics.HEADING_SPACE_ABOVE;
            PdfPages.Bookmark section = pdf.bookmarks().get(1);
            assertThat(section.pageNumber).isEqualTo(1);
            assertThat(section.top).isEqualTo(Math.round(expectedTop));
        }
    }

    @Test
    void rejectsASummaryThatLeavesNoRoomOnPageOne() {
        assertThatThrownBy(() -> ReportDocumentWriters.open(TestReports.spec(30).build(), workDir))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepTogetherMovesASectionWhoseFirstRowWouldNotFit() throws IOException {
        // Page 1 free body after a 2-row summary: 416 - 81 = 335 pt. Section A uses 33 (heading) + 15 (header).
        int rowsLeavingRoom = 14;   // 287 - 14 * 15 = 77 pt left: B's heading + header + row (63 pt) fit
        int rowsLeavingNoRoom = 17; // 287 - 17 * 15 = 32 pt left: B moves to page 2
        assertThat(sectionStarts(rowsLeavingRoom)).containsExactly(1, 1);
        assertThat(sectionStarts(rowsLeavingNoRoom)).containsExactly(1, 2);
    }

    @Test
    void rowsAreNeverSplitAndTheHeaderRepeatsOnEveryContinuationPage() throws IOException {
        int rows = 120;
        try (PdfPages pdf = render(1, writer -> {
            writer.beginSection(section("Long"));
            for (int i = 0; i < rows; i++) {
                writer.addRow(row(String.format("K%03d", i), i % 5 == 0 ? "first line second line third line "
                        .repeat(6) : "short"));
            }
            writer.endSection();
        }, summary(1, rows))) {
            assertThat(pdf.pageCount()).isGreaterThan(2);
            int keys = 0;
            for (int page = 2; page <= pdf.pageCount(); page++) {
                List<String> lines = pdf.lines(page);
                assertThat(lines.get(1)).isEqualTo("Long (continued)");
                assertThat(lines.get(2)).isEqualTo("Key Text");
            }
            for (int page = 1; page <= pdf.pageCount(); page++) {
                keys += (int) pdf.lines(page).stream().filter(line -> line.matches("K\\d{3}.*")).count();
            }
            assertThat(keys).isEqualTo(rows);
        }
    }

    @Test
    void aRowTallerThanAPageIsCutWithAnEllipsis() throws IOException {
        String huge = "lorem ipsum dolor sit amet ".repeat(2_000);
        try (PdfPages pdf = render(1, writer -> {
            writer.beginSection(section("Big"));
            writer.addRow(row("before", "x"));
            writer.addRow(row("huge", huge));
            writer.addRow(row("after", "y"));
            writer.endSection();
        }, summary(1, 3))) {
            assertThat(pdf.pageCount()).isEqualTo(3);
            assertThat(pdf.text(2)).contains("huge", TextWrapper.ELLIPSIS).contains("Big (continued)");
            assertThat(pdf.text(3)).contains("after");
        }
    }

    @Test
    void noWrapColumnsAreTruncatedWithAnEllipsis() throws IOException {
        try (PdfPages pdf = render(1, writer -> {
            writer.beginSection(section("A"));
            writer.addRow(row("KEY-THAT-IS-FAR-TOO-LONG-FOR-ITS-NO-WRAP-COLUMN-".repeat(6), "v"));
            writer.endSection();
        }, summary(1, 1))) {
            assertThat(pdf.text(1)).contains("KEY-THAT-IS-FAR-TOO-LONG").contains(TextWrapper.ELLIPSIS);
        }
    }

    @Test
    void unusualCharactersAreSanitizedInsteadOfFailing() throws IOException {
        try (PdfPages pdf = render(1, writer -> {
            writer.beginSection(section("A\u0000"));
            writer.addRow(row("tab\there", "emoji 😀 cjk 中 bell\u0007"));
            writer.endSection();
        }, summary(1, 1))) {
            assertThat(pdf.text(1)).contains("tab here", "emoji ? cjk ? bell?");
        }
    }

    @Test
    void emptySectionWithoutMessageStillShowsHeadingAndHeader() throws IOException {
        try (PdfPages pdf = render(1, writer -> {
            writer.beginSection(section("Nothing"));
            writer.endSection();
        }, summary(1, 0))) {
            List<String> lines = pdf.lines(1);
            int heading = lines.indexOf("Nothing (0 records)");
            assertThat(heading).isNotNegative();
            assertThat(lines.get(heading + 1)).isEqualTo("Key Text");
        }
    }

    @Test
    void writesDocumentMetadata() throws IOException {
        try (PdfPages pdf = render(1, writer -> {
        }, summary(0, 0))) {
            assertThat(pdf.document().getDocumentInformation().getTitle()).isEqualTo(TestReports.TITLE
                    + " FIPS 51059");
            assertThat(pdf.document().getDocumentCatalog().getLanguage()).isEqualTo("en-US");
            assertThat(pdf.document().getDocumentCatalog().getViewerPreferences().displayDocTitle()).isTrue();
            assertThat(pdf.pageCount()).isEqualTo(1);
            assertThat(pdf.text(1)).contains("Page 1 of 1");
        }
    }

    @Test
    void enforcesTheCallOrder() throws IOException {
        try (ReportDocumentWriter writer = ReportDocumentWriters.open(TestReports.spec(1).build(), workDir)) {
            assertThatThrownBy(() -> writer.addRow(row("a", "b"))).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(writer::endSection).isInstanceOf(IllegalStateException.class);
            writer.beginSection(section("A"));
            assertThatThrownBy(() -> writer.beginSection(section("B"))).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> writer.addRow(List.of(CellValue.of("only one"))))
                    .isInstanceOf(IllegalArgumentException.class);
            writer.addRow(row("a", "b"));
            assertThatThrownBy(() -> writer.addEmptyMessage("none")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> writer.finish(summary(1, 1), new ByteArrayOutputStream()))
                    .isInstanceOf(IllegalStateException.class);
            writer.endSection();
            assertThatThrownBy(() -> writer.finish(summary(2, 1), new ByteArrayOutputStream()))
                    .isInstanceOf(IllegalArgumentException.class);
            writer.finish(summary(1, 1), new ByteArrayOutputStream());
            assertThatThrownBy(() -> writer.beginSection(section("C"))).isInstanceOf(IllegalStateException.class);
        }
        try (ReportDocumentWriter writer = ReportDocumentWriters.open(TestReports.spec(1).build(), workDir)) {
            writer.beginSection(section("A"));
            writer.addEmptyMessage("none");
            assertThatThrownBy(() -> writer.addEmptyMessage("again")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> writer.addRow(row("a", "b"))).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void taggingIsNotAvailableBeforePhaseFive() {
        assertThatThrownBy(() -> ReportDocumentWriters.open(TestReports.spec(1).taggingEnabled(true).build(),
                workDir)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void specsValidateRequiredFields() {
        assertThatThrownBy(() -> TestReports.spec(1).title(" ").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TestReports.spec(-1).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TestReports.spec(1).logo(new byte[0], "alt").build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SectionSpec.builder().heading("h").build()).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SummaryData.builder().addRow("x", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RenderResult.builder().build()).isInstanceOf(IllegalArgumentException.class);

        DocumentSpec spec = TestReports.spec(1).language("de-DE").build();
        assertThat(spec.language()).isEqualTo("de-DE");
        assertThat(spec.logo()).isNotEmpty();
        assertThat(spec.subject()).isEqualTo("FIPS 51059");
        assertThat(spec.author()).isEqualTo("Test Agency");
        assertThat(spec.logContext()).isEqualTo("51059");
        assertThat(DocumentSpec.builder().fonts(TestReports.fonts()).logo(TestReports.logo(), "alt").title("T")
                .reportId("R").build().metadataTitle()).isEqualTo("T");
        assertThat(SummaryData.builder().heading("H").labelHeader("L").countHeader("C").totalLabel("T")
                .addRow("a", 2).addRow("b", 3).build().total()).isEqualTo(5);
        assertThat(section("X").headingFor(12_345)).isEqualTo("X (12,345 records)");
        assertThat(section("X").headingFor(1)).isEqualTo("X (1 records)");
        SectionSpec singular = SectionSpec.builder().heading("X ({n} records)").singularHeading("X ({n} record)")
                .continuationLabel("X (continued)").table(NARROW).build();
        assertThat(singular.headingFor(1)).isEqualTo("X (1 record)");
        assertThat(singular.headingFor(0)).isEqualTo("X (0 records)");
    }

    private List<Integer> sectionStarts(int rowsInFirstSection) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ReportDocumentWriter writer = ReportDocumentWriters.open(TestReports.spec(2).build(), workDir)) {
            writer.beginSection(section("A"));
            for (int i = 0; i < rowsInFirstSection; i++) {
                writer.addRow(row("k" + i, "v"));
            }
            writer.endSection();
            writer.beginSection(section("B"));
            writer.addRow(row("b", "v"));
            writer.endSection();
            return writer.finish(summary(2, rowsInFirstSection + 1), out).sectionFirstPages();
        }
    }

    private PdfPages render(int summaryRows, WriterBody body, SummaryData summary) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ReportDocumentWriter writer = ReportDocumentWriters.open(TestReports.spec(summaryRows).build(),
                workDir)) {
            body.write(writer);
            writer.finish(summary, out);
        }
        return new PdfPages(out.toByteArray());
    }

    private static SectionSpec section(String name) {
        return SectionSpec.builder()
                .heading(name + " ({n} records)")
                .continuationLabel(name + " (continued)")
                .table(NARROW)
                .build();
    }

    private static List<CellValue> row(String key, String text) {
        return List.of(CellValue.of(key), CellValue.of(text));
    }

    private static SummaryData summary(int categories, long total) {
        SummaryData.Builder builder = SummaryData.builder();
        for (int i = 0; i < categories; i++) {
            builder.addRow("Category " + (i + 1), i == 0 ? total : 0);
        }
        return builder.build();
    }

    private static long fileCount(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.count();
        }
    }

    @FunctionalInterface
    private interface WriterBody {
        void write(ReportDocumentWriter writer) throws IOException;
    }
}
