package com.example.reporting.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.reporting.RenderingException;
import com.example.reporting.ReportDataException;
import com.example.reporting.TransientGenerationException;
import com.example.reporting.data.FipsWorkload;
import com.example.reporting.data.ReferenceDataRepository;
import com.example.reporting.data.ReportCategory;
import com.example.reporting.data.ReportDataProvider;
import com.example.reporting.data.ReportRow;
import com.example.reporting.pdf.RenderResult;
import com.example.reporting.profile.ProfileRegistry;
import com.example.reporting.profile.ProfileValidator;
import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.profile.TestProfiles;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

/** The composer's skeleton and error rules, with an in-memory data source instead of the database. */
class FipsReportComposerTest {

    private static final List<ReportCategory> CATEGORIES = List.of(new ReportCategory("CAT1", "Fees", 1),
            new ReportCategory("CAT2", "Permits", 2), new ReportCategory("CAT3", "Liens", 3),
            new ReportCategory("CAT4", "Inspections", 4));

    @TempDir
    Path workDir;

    private final ReferenceDataRepository referenceData = mock(ReferenceDataRepository.class);
    private final FakeRows rows = new FakeRows();
    private FipsReportComposer composer;

    @BeforeEach
    void setUp() {
        ReportingProperties properties = TestProfiles.virginiaProperties(workDir);
        DefaultResourceLoader loader = new DefaultResourceLoader();
        ProfileRegistry registry = new ProfileRegistry(properties, mock(ProfileValidator.class), loader);
        composer = new FipsReportComposer(registry, referenceData, rows,
                new DocumentSpecFactory(properties, loader), properties);
        when(referenceData.localityName("VA", "51059")).thenReturn(Optional.of("Fairfax County"));
        when(referenceData.activeCategories("VA")).thenReturn(CATEGORIES);
    }

    @Test
    void emitsEveryCategoryInOrderWithEmptyOnesBeforeBetweenAndAfterRows() throws IOException {
        rows.add(row("CAT2", 2, "VA-1", new BigDecimal("12.5")));
        rows.add(row("CAT2", 2, "VA-2", null));
        rows.add(row("CAT3", 3, "VA-3", new BigDecimal("-1234")));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RenderResult result = composer.compose(request("51059"), out);

        assertThat(result.rowsWritten()).isEqualTo(3);
        PdfText pdf = new PdfText(out.toByteArray());
        assertThat(pdf.bookmarks()).containsExactly("Summary", "Category 1 – Fees (0 records)",
                "Category 2 – Permits (2 records)", "Category 3 – Liens (1 record)",
                "Category 4 – Inspections (0 records)");
        assertThat(pdf.summaryLines()).containsExactly("Category 1 – Fees 0", "Category 2 – Permits 2",
                "Category 3 – Liens 1", "Category 4 – Inspections 0", "Total 3");
        List<String> lines = pdf.allLines();
        assertThat(lines.stream().filter(l -> l.equals("No content to display in this category."))).hasSize(2);
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("VA-1").contains("03/04/2026", "$12.50"));
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("VA-3").contains("-$1,234.00"));
    }

    @Test
    void aReportWithoutRowsShowsEveryCategoryAsEmpty() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(composer.compose(request("51059"), out).rowsWritten()).isZero();
        assertThat(new PdfText(out.toByteArray()).summaryLines()).last().isEqualTo("Total 0");
    }

    @Test
    void unknownCategoryIsADataError() {
        rows.add(row("CAT1", 1, "VA-1", null));
        rows.add(row("CATX", null, "VA-2", null));
        assertThatThrownBy(() -> composer.compose(request("51059"), new ByteArrayOutputStream()))
                .isInstanceOf(ReportDataException.class)
                .hasMessageContaining("category CATX")
                .hasMessageNotContaining("VA-2");
    }

    @Test
    void unknownFipsCodeIsADataError() {
        when(referenceData.localityName("VA", "51999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> composer.compose(request("51999"), new ByteArrayOutputStream()))
                .isInstanceOf(ReportDataException.class)
                .hasMessage("FIPS code not in RPT_FIPS for state");
    }

    @Test
    void stateWithoutProfileOrCategoriesIsADataError() {
        ReportRequest maryland = ReportRequest.builder().stateCode("MD").fipsCode("24001").runId(1).docId(1)
                .dataAsOf(Instant.EPOCH).build();
        assertThatThrownBy(() -> composer.compose(maryland, new ByteArrayOutputStream()))
                .isInstanceOf(ReportDataException.class).hasMessageContaining("No profile");
        when(referenceData.activeCategories("VA")).thenReturn(List.of());
        assertThatThrownBy(() -> composer.compose(request("51059"), new ByteArrayOutputStream()))
                .isInstanceOf(ReportDataException.class).hasMessageContaining("no active categories");
    }

    @Test
    void rowsOutOfDisplayOrderAreARenderingError() {
        rows.add(row("CAT3", 3, "VA-1", null));
        rows.add(row("CAT1", 1, "VA-2", null));
        assertThatThrownBy(() -> composer.compose(request("51059"), new ByteArrayOutputStream()))
                .isInstanceOf(RenderingException.class);
    }

    @Test
    void anInterruptedWorkerStopsWithATransientError() {
        rows.add(row("CAT1", 1, "VA-1", null));
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> composer.compose(request("51059"), new ByteArrayOutputStream()))
                    .isInstanceOf(TransientGenerationException.class)
                    .satisfies(e -> assertThat(((TransientGenerationException) e).isRetryable()).isTrue());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void writeFailuresBecomeRenderingErrors() {
        rows.add(row("CAT1", 1, "VA-1", null));
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("stream closed");
            }
        };
        assertThatThrownBy(() -> composer.compose(request("51059"), broken))
                .isInstanceOf(RenderingException.class)
                .satisfies(e -> assertThat(((RenderingException) e).isRetryable()).isFalse());
    }

    @Test
    void aFullTempDiskIsTransient() {
        rows.add(row("CAT1", 1, "VA-1", null));
        OutputStream full = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("No space left on device");
            }
        };
        assertThatThrownBy(() -> composer.compose(request("51059"), full))
                .isInstanceOf(TransientGenerationException.class);
    }

    private static ReportRequest request(String fips) {
        return ReportRequest.builder().stateCode("VA").fipsCode(fips).runId(7).docId(11)
                .dataAsOf(Instant.parse("2026-11-10T07:00:00Z")).build();
    }

    private static ReportRow row(String category, Integer order, String caseNumber, BigDecimal amount) {
        return ReportRow.builder().categoryCode(category).displayOrder(order).caseNumber(caseNumber)
                .lastName("Testerson").firstName("Alpha").eventDate(LocalDate.of(2026, 3, 4)).amount(amount)
                .status("OPEN").description("Sample record.").build();
    }

    /** In-memory provider; streams the rows added to it. */
    private static final class FakeRows implements ReportDataProvider {
        private final List<ReportRow> rows = new ArrayList<>();

        void add(ReportRow row) {
            rows.add(row);
        }

        @Override
        public List<FipsWorkload> workload(String stateCode) {
            return List.of();
        }

        @Override
        public long streamRows(String stateCode, String fipsCode, Consumer<ReportRow> consumer) {
            rows.forEach(consumer);
            return rows.size();
        }
    }
}
