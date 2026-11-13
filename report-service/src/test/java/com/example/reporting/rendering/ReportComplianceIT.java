package com.example.reporting.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;

/**
 * Phase 5 acceptance on real reports: PDFs composed from the seeded data with the shipped configuration
 * ({@code reporting.tagging-enabled: true}) have zero veraPDF failures for PDF/UA-1 and PDF/A-2a.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles({"h2", "test"})
@TestConstructor(autowireMode = AutowireMode.ALL)
class ReportComplianceIT {

    static {
        VeraGreenfieldFoundryProvider.initialise();
    }

    private final FipsReportComposer composer;

    ReportComplianceIT(FipsReportComposer composer) {
        this.composer = composer;
    }

    @ParameterizedTest(name = "FIPS {0} is {1}")
    @CsvSource({"51013, PDFUA_1", "51013, PDFA_2_A", "51059, PDFUA_1", "51059, PDFA_2_A"})
    void realReportsPassVeraPdf(String fips, String flavourName) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        composer.compose(ReportRequest.builder().stateCode("VA").fipsCode(fips).runId(9).docId(9)
                .dataAsOf(Instant.parse("2026-11-10T07:00:00Z")).build(), out);
        PDFAFlavour flavour = PDFAFlavour.valueOf(flavourName);
        try (PDFAParser parser = Foundries.defaultInstance().createParser(
                new ByteArrayInputStream(out.toByteArray()), flavour)) {
            ValidationResult result = Foundries.defaultInstance().createValidator(flavour, false).validate(parser);
            List<String> failures = result.getTestAssertions().stream()
                    .filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
                    .map(a -> a.getRuleId().getClause() + "-" + a.getRuleId().getTestNumber() + " " + a.getMessage())
                    .distinct().collect(Collectors.toList());
            assertThat(failures).as("veraPDF %s failures for FIPS %s", flavour, fips).isEmpty();
            assertThat(result.isCompliant()).isTrue();
        }
    }
}
