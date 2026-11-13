package com.example.reporting.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Backoff of SPEC section 17. A random value of 0.5 means no jitter. */
class BackoffTest {

    @Test
    void generationDefaultsDoubleFromOneMinute() {
        Backoff backoff = new Backoff(Duration.ofMinutes(1), 2.0, Duration.ofMinutes(30), () -> 0.5);
        assertThat(backoff.delay(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(backoff.delay(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(backoff.delay(6)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void sendDefaultsReachTheOneHourCapAtTheSeventhAttempt() {
        Backoff backoff = new Backoff(Duration.ofMinutes(1), 2.0, Duration.ofHours(1), () -> 0.5);
        List<Long> minutes = new ArrayList<>();
        for (int attempt = 1; attempt <= 7; attempt++) {
            minutes.add(backoff.delay(attempt).toMinutes());
        }
        assertThat(minutes).containsExactly(1L, 2L, 4L, 8L, 16L, 32L, 60L);
    }

    @Test
    void jitterStaysWithinTenPercent() {
        assertThat(new Backoff(Duration.ofMinutes(10), 2.0, Duration.ofHours(1), () -> 0.0).delay(1))
                .isEqualTo(Duration.ofMinutes(9));
        assertThat(new Backoff(Duration.ofMinutes(10), 2.0, Duration.ofHours(1), () -> 0.999_999).delay(1))
                .isBetween(Duration.ofSeconds(659), Duration.ofMinutes(11));
        Backoff random = new Backoff(Duration.ofMinutes(1), 2.0, Duration.ofMinutes(30));
        for (int i = 0; i < 100; i++) {
            assertThat(random.delay(3)).isBetween(Duration.ofSeconds(216), Duration.ofSeconds(264));
        }
    }

    @Test
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new Backoff(Duration.ofMinutes(1), 0.5, Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Backoff(Duration.ofMinutes(10), 2.0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Backoff(Duration.ofMinutes(1), 2.0, Duration.ofMinutes(30)).delay(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void errorTextsAreCutToTheColumnSize() {
        assertThat(DocumentClaimRepository.truncate(null)).isNull();
        assertThat(DocumentClaimRepository.truncate("short")).isEqualTo("short");
        assertThat(DocumentClaimRepository.truncate("x".repeat(2_500))).hasSize(2_000);
    }
}
