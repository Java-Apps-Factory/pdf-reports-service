package com.example.reporting.pdf.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextWrapperTest {

    // Size 10 with the fake measurer: 5 pt per character.
    private static final float SIZE = 10f;
    private final TextMeasurer measurer = new FakeMeasurer();

    @Test
    void wrapsOnSpaces() {
        // 50 pt = 10 characters per line
        assertThat(TextWrapper.wrap("alpha beta gamma delta", measurer, SIZE, 50f))
                .containsExactly("alpha beta", "gamma", "delta");
    }

    @Test
    void breaksWordsLongerThanTheLine() {
        assertThat(TextWrapper.wrap("abcdefghijklmnopqrstuvwxy z", measurer, SIZE, 50f))
                .containsExactly("abcdefghij", "klmnopqrst", "uvwxy z");
    }

    @Test
    void collapsesRepeatedSpacesAndTrims() {
        assertThat(TextWrapper.wrap("  a    b  ", measurer, SIZE, 50f)).containsExactly("a b");
    }

    @Test
    void emptyTextGivesOneEmptyLine() {
        assertThat(TextWrapper.wrap("", measurer, SIZE, 50f)).containsExactly("");
        assertThat(TextWrapper.wrap("   ", measurer, SIZE, 50f)).containsExactly("");
    }

    @Test
    void alwaysMakesProgressWhenOneCharacterIsWiderThanTheLine() {
        assertThat(TextWrapper.wrap("abc", measurer, SIZE, 2f)).containsExactly("a", "b", "c");
    }

    @Test
    void truncateKeepsFittingText() {
        assertThat(TextWrapper.truncate("short", measurer, SIZE, 50f)).isEqualTo("short");
    }

    @Test
    void truncateEndsInEllipsis() {
        String result = TextWrapper.truncate("abcdefghijklmnop", measurer, SIZE, 50f);
        assertThat(result).isEqualTo("abcdefghi" + TextWrapper.ELLIPSIS);
        assertThat(measurer.width(result, SIZE)).isLessThanOrEqualTo(50f);
    }

    @Test
    void truncateDropsTrailingSpaceBeforeEllipsis() {
        assertThat(TextWrapper.truncate("abcdefgh  xyz", measurer, SIZE, 50f))
                .isEqualTo("abcdefgh" + TextWrapper.ELLIPSIS);
    }

    @Test
    void truncateGivesEmptyWhenNotEvenTheEllipsisFits() {
        assertThat(TextWrapper.truncate("abc", measurer, SIZE, 2f)).isEmpty();
    }

    @Test
    void limitLinesEndsLastVisibleLineInEllipsis() {
        List<String> lines = List.of("abcdefghij", "klmnopqrst", "uvwxyz");
        assertThat(TextWrapper.limitLines(lines, 2, measurer, SIZE, 50f))
                .containsExactly("abcdefghij", "klmnopqrs" + TextWrapper.ELLIPSIS);
        assertThat(TextWrapper.limitLines(lines, 3, measurer, SIZE, 50f)).isSameAs(lines);
    }

    @Test
    void limitLinesRejectsZero() {
        assertThatThrownBy(() -> TextWrapper.limitLines(List.of("a"), 0, measurer, SIZE, 50f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handlesSurrogatePairs() {
        String text = "😀😀😀";
        assertThat(TextWrapper.wrap(text, measurer, SIZE, 10f)).hasSize(2);
        assertThat(TextWrapper.truncate(text, measurer, SIZE, 10f)).isEqualTo("😀" + TextWrapper.ELLIPSIS);
    }
}
