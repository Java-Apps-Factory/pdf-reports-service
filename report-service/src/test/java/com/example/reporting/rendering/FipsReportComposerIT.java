package com.example.reporting.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reporting.ReportDataException;
import com.example.reporting.data.JdbcReportDataProvider;
import com.example.reporting.pdf.RenderResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * Phase 2 acceptance (SPEC section 21): real PDFs from the seeded H2 data, with summary counts equal to the
 * rows rendered and to the database, and data errors for unknown categories and FIPS codes.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles({"h2", "test"})
@TestConstructor(autowireMode = AutowireMode.ALL)
class FipsReportComposerIT {

    /** Seeded FIPS code holding about 40% of all records (DevRecordGenerator.LARGE_FIPS). */
    private static final String LARGE_FIPS = "51059";
    /** Seeded FIPS code that never has a CAT3 record (DevRecordGenerator.EMPTY_CATEGORY_FIPS). */
    private static final String EMPTY_CATEGORY_FIPS = "51013";

    private final FipsReportComposer composer;
    private final JdbcReportDataProvider dataProvider;
    private final JdbcTemplate jdbc;

    FipsReportComposerIT(FipsReportComposer composer, JdbcReportDataProvider dataProvider, JdbcTemplate jdbc) {
        this.composer = composer;
        this.dataProvider = dataProvider;
        this.jdbc = jdbc;
    }

    @Test
    void largeFipsCodeSummaryEqualsRowsRenderedAndDatabaseCounts() throws IOException {
        Map<String, Long> dbCounts = countsByCategory(LARGE_FIPS);
        byte[] pdf = render(LARGE_FIPS);
        PdfText text = new PdfText(pdf);
        long total = dbCounts.values().stream().mapToLong(Long::longValue).sum();

        assertThat(text.pageCount()).isGreaterThan(5);
        assertThat(text.summaryLines()).containsExactly(
                summaryLine(1, dbCounts.get("CAT1")), summaryLine(2, dbCounts.get("CAT2")),
                summaryLine(3, dbCounts.get("CAT3")), summaryLine(4, dbCounts.get("CAT4")),
                "Total " + String.format(Locale.US, "%,d", total));
        List<String> caseLines = text.allLines().stream().filter(l -> l.matches("VA-\\d{8}.*"))
                .collect(Collectors.toList());
        assertThat(caseLines).hasSize((int) total);
        assertThat(caseLines.stream().map(l -> l.substring(0, 11)).distinct().count()).isEqualTo(total);
        assertThat(text.bookmarks()).hasSize(5).first().isEqualTo("Summary");
        assertThat(text.page(1)).contains("FIPS 51059 – Fairfax County", "Data as of 11/10/2026 02:00 AM ET",
                "Report ID R-1-2");
    }

    @Test
    void emptyCategoryIsShownWithItsMessage() throws IOException {
        Map<String, Long> dbCounts = countsByCategory(EMPTY_CATEGORY_FIPS);
        assertThat(dbCounts).doesNotContainKey("CAT3");
        PdfText text = new PdfText(render(EMPTY_CATEGORY_FIPS));

        assertThat(text.summaryLines()).contains("Category 3 – Category 3 0");
        assertThat(text.bookmarks()).contains("Category 3 – Category 3 (0 records)");
        assertThat(text.allLines()).contains("No content to display in this category.");
    }

    @Test
    void renderResultMatchesTheStreamedRowCount() {
        long streamed = dataProvider.streamRows("VA", EMPTY_CATEGORY_FIPS, row -> { });
        RenderResult result = composer.compose(request(EMPTY_CATEGORY_FIPS), new ByteArrayOutputStream());
        assertThat(result.rowsWritten()).isEqualTo(streamed).isPositive();
        assertThat(dataProvider.workload("VA")).anySatisfy(w -> {
            assertThat(w.fipsCode()).isEqualTo(EMPTY_CATEGORY_FIPS);
            assertThat(w.recordCount()).isEqualTo(streamed);
            assertThat(w.unknownFips()).isFalse();
        });
    }

    @Test
    void unknownCategoryProducesReportDataException() {
        // Accomack (51001) has no seeded records; give it one record in a category VA does not have.
        jdbc.update("INSERT INTO RPT_RECORD (STATE_CODE, FIPS_CODE, CATEGORY_CODE, CASE_NUMBER, LAST_NAME) "
                + "VALUES ('VA', '51001', 'CATX', 'VA-99999999', 'Testerson')");
        try {
            assertThatThrownBy(() -> composer.compose(request("51001"), new ByteArrayOutputStream()))
                    .isInstanceOf(ReportDataException.class)
                    .hasMessageContaining("CATX")
                    .hasMessageNotContaining("Testerson");
        } finally {
            jdbc.update("DELETE FROM RPT_RECORD WHERE FIPS_CODE = '51001'");
        }
    }

    @Test
    void unknownFipsCodeProducesReportDataException() {
        assertThatThrownBy(() -> composer.compose(request("51999"), new ByteArrayOutputStream()))
                .isInstanceOf(ReportDataException.class)
                .hasMessage("FIPS code not in RPT_FIPS for state");
    }

    private byte[] render(String fips) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RenderResult result = composer.compose(request(fips), out);
        assertThat(result.pageCount()).isPositive();
        Path target = Paths.get("target");
        Files.createDirectories(target);
        Files.write(target.resolve("phase2-VA-" + fips + ".pdf"), out.toByteArray());
        return out.toByteArray();
    }

    private Map<String, Long> countsByCategory(String fips) {
        List<Map<String, Object>> rows = new ArrayList<>(jdbc.queryForList(
                "SELECT CATEGORY_CODE, COUNT(*) AS N FROM RPT_RECORD WHERE STATE_CODE = 'VA' AND FIPS_CODE = ? "
                        + "GROUP BY CATEGORY_CODE", fips));
        return rows.stream().collect(Collectors.toMap(r -> ((String) r.get("CATEGORY_CODE")).trim(),
                r -> ((Number) r.get("N")).longValue()));
    }

    private static String summaryLine(int order, Long count) {
        return "Category " + order + " – Category " + order + " "
                + String.format(Locale.US, "%,d", count == null ? 0 : count);
    }

    private static ReportRequest request(String fips) {
        return ReportRequest.builder().stateCode("VA").fipsCode(fips).runId(1).docId(2)
                .dataAsOf(Instant.parse("2026-11-10T07:00:00Z")).build();
    }
}
