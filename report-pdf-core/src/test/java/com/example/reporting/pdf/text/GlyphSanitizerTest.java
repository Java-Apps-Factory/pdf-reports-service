package com.example.reporting.pdf.text;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reporting.pdf.TestReports;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.Test;

class GlyphSanitizerTest {

    @Test
    void replacesControlCharactersAndKeepsWhitespaceAsSpaces() {
        GlyphSanitizer sanitizer = new GlyphSanitizer(cp -> true);
        assertThat(sanitizer.sanitize("a\u0000b\u0007c\td\ne\rf​g")).isEqualTo("a?b?c d e f?g");
    }

    @Test
    void replacesCharactersTheFontCannotRender() {
        GlyphSanitizer sanitizer = new GlyphSanitizer(cp -> cp < 128);
        assertThat(sanitizer.sanitize("café 😀")).isEqualTo("caf? ?");
    }

    @Test
    void treatsNullAsEmpty() {
        GlyphSanitizer sanitizer = new GlyphSanitizer(cp -> true);
        assertThat(sanitizer.sanitize(null)).isEmpty();
        assertThat(sanitizer.sanitize("")).isEmpty();
    }

    @Test
    void checksGlyphsAgainstTheRealFont() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = PDType0Font.load(document,
                    new ByteArrayInputStream(TestReports.fonts().regular()), true);
            GlyphSanitizer sanitizer = GlyphSanitizer.forFont(font);
            // Latin with accents and the en dash are in Noto Sans; CJK and emoji are not.
            assertThat(sanitizer.sanitize("José – Müller")).isEqualTo("José – Müller");
            assertThat(sanitizer.sanitize("中😀x")).isEqualTo("??x");

            TextMeasurer measurer = TextMeasurer.forFont(font);
            float a = measurer.width("a", 10f);
            assertThat(a).isPositive();
            assertThat(measurer.width("aa", 10f)).isCloseTo(2 * a, org.assertj.core.data.Offset.offset(0.001f));
            assertThat(measurer.width("a", 20f)).isCloseTo(2 * a, org.assertj.core.data.Offset.offset(0.001f));
        }
    }
}
