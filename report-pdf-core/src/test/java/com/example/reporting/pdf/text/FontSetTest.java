package com.example.reporting.pdf.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FontSetTest {

    @Test
    void copiesBytesInAndOut() {
        byte[] regular = {1, 2, 3};
        FontSet set = FontSet.builder().regular(regular).bold(new byte[] {4}).italic(new byte[] {5}).build();
        regular[0] = 9;
        assertThat(set.regular()).containsExactly(1, 2, 3);
        set.regular()[0] = 9;
        assertThat(set.regular()).containsExactly(1, 2, 3);
        assertThat(set.bold()).containsExactly(4);
        assertThat(set.italic()).containsExactly(5);
    }

    @Test
    void rejectsMissingOrEmptyFonts() {
        assertThatThrownBy(() -> FontSet.builder().bold(new byte[] {1}).italic(new byte[] {1}).build())
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> FontSet.builder().regular(new byte[0]).bold(new byte[] {1})
                .italic(new byte[] {1}).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
