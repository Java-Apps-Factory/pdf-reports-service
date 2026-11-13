package com.example.reporting.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reporting.data.ReportCategory;
import com.example.reporting.pdf.DocumentSpec;
import com.example.reporting.pdf.SectionSpec;
import com.example.reporting.pdf.table.Alignment;
import com.example.reporting.pdf.table.ColumnSpec;
import com.example.reporting.profile.ColumnDefinition;
import com.example.reporting.profile.ColumnType;
import com.example.reporting.profile.StateProfile;
import com.example.reporting.profile.TestProfiles;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class DocumentSpecFactoryTest {

    private final DocumentSpecFactory factory = new DocumentSpecFactory(
            TestProfiles.virginiaProperties(Paths.get("tmp")), new DefaultResourceLoader());
    private final ReportCategory fees = new ReportCategory("CAT1", "Fees", 1);

    @Test
    void dataAsOfIsTheRunStartInTheBusinessZone() {
        // 07:00 UTC in November is 02:00 Eastern Standard Time; in July 06:00 UTC is 02:00 EDT.
        assertThat(factory.dataAsOfLine(Instant.parse("2026-11-10T07:00:00Z")))
                .isEqualTo("Data as of 11/10/2026 02:00 AM ET");
        assertThat(factory.dataAsOfLine(Instant.parse("2026-07-01T18:30:00Z")))
                .isEqualTo("Data as of 07/01/2026 02:30 PM ET");
    }

    @Test
    void buildsTheTitleBlockRunningHeaderAndFooterFromTheProfile() {
        ReportRequest request = ReportRequest.builder().stateCode("VA").fipsCode("51059").runId(1702).docId(48213)
                .dataAsOf(Instant.parse("2026-11-10T07:00:00Z")).build();
        DocumentSpec spec = factory.documentSpec(request, TestProfiles.virginia(), new byte[] {1}, "Fairfax County", 4);

        assertThat(spec.title()).isEqualTo("Commonwealth of Virginia – Locality Report");
        assertThat(spec.subtitle()).isEqualTo("Virginia");
        assertThat(spec.fipsLine()).isEqualTo("FIPS 51059 – Fairfax County");
        assertThat(spec.infoLines()).containsExactly("Data as of 11/10/2026 02:00 AM ET",
                "Report ID R-1702-48213");
        assertThat(spec.runningHeaderLeft())
                .isEqualTo("Commonwealth of Virginia – Locality Report – FIPS 51059 Fairfax County");
        assertThat(spec.runningHeaderRight()).isEqualTo("Data as of 11/10/2026 02:00 AM ET");
        assertThat(spec.reportId()).isEqualTo("R-1702-48213");
        assertThat(spec.footerDisclaimer()).startsWith("Official use only");
        assertThat(spec.logoAltText()).isEqualTo("Placeholder agency logo");
        assertThat(spec.summaryRowCount()).isEqualTo(4);
        assertThat(spec.language()).isEqualTo("en-US");
        assertThat(spec.taggingEnabled()).isFalse();
        assertThat(spec.logContext()).doesNotContain("Fairfax").contains("51059");
    }

    @Test
    void sectionsUseCategoryLabelsAndProfileColumns() {
        SectionSpec section = factory.sectionSpec(TestProfiles.virginia(), fees);
        assertThat(section.headingFor(2_000)).isEqualTo("Category 1 – Fees (2,000 records)");
        assertThat(section.headingFor(1)).isEqualTo("Category 1 – Fees (1 record)");
        assertThat(section.continuationLabel()).isEqualTo("Fees (continued)");
        List<ColumnSpec> columns = section.table().columns();
        assertThat(columns).extracting(ColumnSpec::label).containsExactly("Case Number", "Last Name", "First Name",
                "Event Date", "Amount", "Status", "Description");
        assertThat(columns.get(4).alignment()).isEqualTo(Alignment.RIGHT);
        assertThat(columns.get(3).alignment()).isEqualTo(Alignment.LEFT);
        assertThat(columns.get(0).wrap()).isFalse();
        assertThat(columns.get(1).wrap()).isTrue();
        assertThat(columns.get(4).wrap()).isFalse();
    }

    @Test
    void categoryOverridesReplaceTheDefaultColumns() {
        List<ColumnDefinition> override = List.of(new ColumnDefinition("caseNumber", "Case", ColumnType.TEXT, 60, null),
                new ColumnDefinition("amount", "Fee", ColumnType.CURRENCY, 40, null));
        StateProfile profile = TestProfiles.profile(TestProfiles.virginiaColumns(), Map.of("CAT1", override),
                TestProfiles.LOGO, "Logo");
        assertThat(factory.sectionSpec(profile, fees).table().columns()).extracting(ColumnSpec::label)
                .containsExactly("Case", "Fee");
        assertThat(factory.sectionSpec(profile, new ReportCategory("CAT2", "Permits", 2)).table().columnCount())
                .isEqualTo(7);
    }
}
