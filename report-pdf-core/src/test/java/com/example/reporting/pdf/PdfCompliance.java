package com.example.reporting.pdf;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.Collectors;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;

/** Runs veraPDF on a PDF and returns the failed rules, one line each. */
final class PdfCompliance {

    static {
        VeraGreenfieldFoundryProvider.initialise();
    }

    private PdfCompliance() {
    }

    static List<String> failures(byte[] pdf, PDFAFlavour flavour) throws Exception {
        try (PDFAParser parser = Foundries.defaultInstance().createParser(new ByteArrayInputStream(pdf), flavour)) {
            PDFAValidator validator = Foundries.defaultInstance().createValidator(flavour, false);
            ValidationResult result = validator.validate(parser);
            return result.getTestAssertions().stream()
                    .filter(assertion -> assertion.getStatus() == TestAssertion.Status.FAILED)
                    .map(assertion -> assertion.getRuleId().getClause() + "-" + assertion.getRuleId().getTestNumber()
                            + " " + assertion.getMessage() + " @ " + assertion.getLocation().getContext())
                    .distinct()
                    .collect(Collectors.toList());
        }
    }
}
