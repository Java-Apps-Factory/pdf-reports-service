package com.example.reporting.pdf.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageCursorTest {

    @Test
    void advancesDownwardsAndTracksRemainingSpace() {
        PageCursor cursor = new PageCursor(1, 500f, 100f);
        assertThat(cursor.isAtTop()).isTrue();
        assertThat(cursor.remaining()).isEqualTo(400f);
        cursor.advance(150f);
        assertThat(cursor.y()).isEqualTo(350f);
        assertThat(cursor.isAtTop()).isFalse();
        assertThat(cursor.fits(250f)).isTrue();
        assertThat(cursor.fits(250.5f)).isFalse();
    }

    @Test
    void refusesToAdvancePastTheBottom() {
        PageCursor cursor = new PageCursor(1, 200f, 100f);
        assertThatThrownBy(() -> cursor.advance(101f)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cursor.advance(-1f)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startPageResetsToTheTop() {
        PageCursor cursor = new PageCursor(1, 200f, 100f);
        cursor.advance(50f);
        cursor.startPage(2, 300f, 50f);
        assertThat(cursor.pageNumber()).isEqualTo(2);
        assertThat(cursor.isAtTop()).isTrue();
        assertThat(cursor.remaining()).isEqualTo(250f);
        assertThatThrownBy(() -> cursor.startPage(0, 300f, 50f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cursor.startPage(3, 10f, 50f)).isInstanceOf(IllegalArgumentException.class);
    }
}
