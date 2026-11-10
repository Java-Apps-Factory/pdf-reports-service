package com.example.reporting.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reporting.pdf.decorate.FooterPainter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 1 acceptance demo (SPEC section 21): a 3-category report with 2,000 rows and one empty category,
 * checked with every PDF assertion of SPEC section 20. The PDF is also written to
 * {@code target/demo-report.pdf} for manual review.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoReportTest {

    private static final String EMPTY_MESSAGE = "No content to display in this category.";
    private static final int FEES_ROWS = 1_200;
    private static final int PERMITS_ROWS = 800;

    private RenderResult result;
    private PdfPages pdf;

    @BeforeAll
    void render(@TempDir Path workDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ReportDocumentWriter writer = ReportDocumentWriters.open(TestReports.spec(3).build(), workDir)) {
            writer.beginSection(TestReports.section(1, "Fees"));
            for (int i = 0; i < FEES_ROWS; i++) {
                writer.addRow(TestReports.row(i));
            }
            writer.endSection();
            writer.beginSection(TestReports.section(2, "Inspections"));
            writer.addEmptyMessage(EMPTY_MESSAGE);
            writer.endSection();
            writer.beginSection(TestReports.section(3, "Permits"));
            for (int i = 0; i < PERMITS_ROWS; i++) {
                writer.addRow(TestReports.row(FEES_ROWS + i));
            }
            writer.endSection();
            SummaryData summary = SummaryData.builder()
                    .addRow("Category 1 – Fees", FEES_ROWS)
                    .addRow("Category 2 – Inspections", 0)
                    .addRow("Category 3 – Permits", PERMITS_ROWS)
                    .build();
            result = writer.finish(summary, out);
        }
        Path target = Paths.get("target");
        Files.createDirectories(target);
        Files.write(target.resolve("demo-report.pdf"), out.toByteArray());
        pdf = new PdfPages(out.toByteArray());
    }

    @Test
    void reportsPageCountAndRowsWritten() {
        assertThat(result.rowsWritten()).isEqualTo(2_000);
        assertThat(result.pageCount()).isEqualTo(pdf.pageCount()).isGreaterThan(40);
    }

    @Test
    void titleBlockIsOnPageOneOnly() {
        assertThat(pdf.text(1)).contains(TestReports.TITLE, "Virginia", "FIPS 51059", "Report ID "
                + TestReports.REPORT_ID);
        for (int page = 2; page <= pdf.pageCount(); page++) {
            assertThat(pdf.text(page)).doesNotContain("Report ID ").doesNotContain("Virginia");
        }
    }

    @Test
    void runningHeaderIsOnPagesTwoAndLater() {
        assertThat(pdf.text(1)).doesNotContain(TestReports.RUNNING_LEFT);
        for (int page = 2; page <= pdf.pageCount(); page++) {
            List<String> lines = pdf.lines(page);
            assertThat(lines.get(0)).contains(TestReports.RUNNING_LEFT).contains(TestReports.RUNNING_RIGHT);
        }
    }

    @Test
    void everyPageHasPageXOfY() {
        int total = pdf.pageCount();
        for (int page = 1; page <= total; page++) {
            assertThat(pdf.text(page)).contains(FooterPainter.pageLabel(page, total))
                    .contains(TestReports.REPORT_ID);
        }
    }

    @Test
    void tableHeaderIsAtTheTopOfEveryContinuationPage() {
        for (int page = 2; page <= pdf.pageCount(); page++) {
            List<String> top = pdf.lines(page).subList(1, 4);
            assertThat(top).as("top lines of page %d", page)
                    .anySatisfy(line -> assertThat(line).contains("Case Number", "Last Name", "Description"));
            assertThat(top).as("label or heading on page %d", page)
                    .anySatisfy(line -> assertThat(line).containsAnyOf("(continued)", "Category "));
        }
    }

    @Test
    void continuationLabelsNameTheirSection() {
        int feesFirst = result.sectionFirstPages().get(0);
        int permitsFirst = result.sectionFirstPages().get(2);
        long feesContinuations = countPagesContaining("Fees (continued)");
        assertThat(feesContinuations).isPositive();
        for (int page = feesFirst + 1; page <= feesFirst + feesContinuations; page++) {
            assertThat(pdf.lines(page).get(1)).isEqualTo("Fees (continued)");
        }
        // Permits is the last section, so every later page continues it.
        assertThat(countPagesContaining("Permits (continued)")).isEqualTo(pdf.pageCount() - permitsFirst);
        assertThat(countPagesContaining("Inspections (continued)")).isZero();
    }

    @Test
    void emptyCategoryShowsHeadingHeaderAndMessage() {
        int page = result.sectionFirstPages().get(1);
        List<String> lines = pdf.lines(page);
        int heading = indexOf(lines, "Category 2 – Inspections (0 records)");
        assertThat(heading).isNotNegative();
        assertThat(lines.get(heading + 1)).contains("Case Number", "Description");
        assertThat(lines.get(heading + 2)).isEqualTo(EMPTY_MESSAGE);
    }

    @Test
    void summaryShowsEveryCategoryWithTotalOnPageOne() {
        List<String> lines = pdf.lines(1);
        assertThat(lines).contains("Summary", "Category Records", "Category 1 – Fees 1,200",
                "Category 2 – Inspections 0", "Category 3 – Permits 800", "Total 2,000");
        assertThat(indexOf(lines, "Total 2,000")).isLessThan(indexOf(lines, "Category 1 – Fees (1,200"));
    }

    @Test
    void bookmarksMatchCategories() throws IOException {
        List<PdfPages.Bookmark> bookmarks = pdf.bookmarks();
        assertThat(bookmarks.stream().map(b -> b.title).collect(Collectors.toList())).containsExactly(
                "Summary",
                "Category 1 – Fees (1,200 records)",
                "Category 2 – Inspections (0 records)",
                "Category 3 – Permits (800 records)");
        assertThat(bookmarks.get(0).pageNumber).isEqualTo(1);
        assertThat(bookmarks.subList(1, 4).stream().map(b -> b.pageNumber).collect(Collectors.toList()))
                .isEqualTo(result.sectionFirstPages());
        for (int i = 1; i < bookmarks.size(); i++) {
            assertThat(pdf.text(bookmarks.get(i).pageNumber)).contains(bookmarks.get(i).title);
        }
    }

    @Test
    void everyRowIsRenderedOnce() {
        String all = String.join("\n", allPages());
        assertThat(all).contains("C-0000000", "C-0001199", "C-0001200", "C-0001999");
        long caseNumbers = all.lines().filter(line -> line.startsWith("C-")).count();
        assertThat(caseNumbers).isEqualTo(2_000);
    }

    private List<String> allPages() {
        List<String> pages = new java.util.ArrayList<>();
        for (int page = 1; page <= pdf.pageCount(); page++) {
            pages.add(pdf.text(page));
        }
        return pages;
    }

    private long countPagesContaining(String text) {
        return allPages().stream().filter(page -> page.contains(text)).count();
    }

    private static int indexOf(List<String> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }
}
