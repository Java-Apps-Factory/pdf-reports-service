package com.example.reporting.tracking;

import java.time.Duration;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Retry delay (SPEC section 17): min(initial × multiplier^(attempt − 1), max), plus random jitter of ±10%.
 * Immutable; the jitter source is injectable for tests.
 */
public final class Backoff {

    private static final double JITTER = 0.10;

    private final Duration initial;
    private final double multiplier;
    private final Duration max;
    private final DoubleSupplier random;

    /**
     * Creates a backoff with {@link Math#random()} jitter.
     *
     * @param initial delay after the first failure
     * @param multiplier growth factor, at least 1
     * @param max upper bound before jitter
     */
    public Backoff(Duration initial, double multiplier, Duration max) {
        this(initial, multiplier, max, Math::random);
    }

    /**
     * Creates a backoff.
     *
     * @param initial delay after the first failure
     * @param multiplier growth factor, at least 1
     * @param max upper bound before jitter
     * @param random source of values in [0, 1)
     */
    public Backoff(Duration initial, double multiplier, Duration max, DoubleSupplier random) {
        this.initial = Objects.requireNonNull(initial, "initial");
        this.max = Objects.requireNonNull(max, "max");
        this.random = Objects.requireNonNull(random, "random");
        if (multiplier < 1.0 || initial.isNegative() || max.compareTo(initial) < 0) {
            throw new IllegalArgumentException("Need multiplier >= 1 and 0 <= initial <= max");
        }
        this.multiplier = multiplier;
    }

    /**
     * Returns the delay before the next attempt.
     *
     * @param attempt number of the attempt that just failed, starting at 1
     * @return delay with jitter
     */
    public Duration delay(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
        double base = initial.toMillis() * Math.pow(multiplier, attempt - 1);
        double capped = Math.min(base, max.toMillis());
        double factor = 1.0 + JITTER * (2.0 * random.getAsDouble() - 1.0);
        return Duration.ofMillis(Math.round(capped * factor));
    }
}
