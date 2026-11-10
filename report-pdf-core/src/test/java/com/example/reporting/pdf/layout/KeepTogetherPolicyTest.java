package com.example.reporting.pdf.layout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeepTogetherPolicyTest {

    @Test
    void sectionStaysWhenHeadingHeaderAndFirstRowFit() {
        PageCursor cursor = new PageCursor(1, 500f, 100f);
        cursor.advance(300f); // 100 pt left
        assertThat(KeepTogetherPolicy.startsOnNewPage(cursor, 33f, 15f, 52f)).isFalse();
    }

    @Test
    void sectionMovesWhenTheFirstRowWouldBeSeparatedFromItsHeading() {
        PageCursor cursor = new PageCursor(1, 500f, 100f);
        cursor.advance(340f); // 60 pt left: heading and header fit, the first row does not
        assertThat(cursor.fits(33f + 15f)).isTrue();
        assertThat(KeepTogetherPolicy.startsOnNewPage(cursor, 33f, 15f, 15f)).isTrue();
    }

    @Test
    void emptySectionNeedsRoomForTheEmptyMessage() {
        PageCursor cursor = new PageCursor(1, 500f, 100f);
        cursor.advance(340f);
        assertThat(KeepTogetherPolicy.startsOnNewPage(cursor, 33f, 15f, LayoutMetrics.emptyMessageHeight()))
                .isTrue();
        assertThat(KeepTogetherPolicy.startsOnNewPage(cursor, 33f, 15f, 0f)).isFalse();
    }

    @Test
    void sectionAtTheTopOfAPageNeverMoves() {
        PageCursor cursor = new PageCursor(2, 120f, 100f);
        assertThat(KeepTogetherPolicy.startsOnNewPage(cursor, 21f, 15f, 500f)).isFalse();
    }
}
