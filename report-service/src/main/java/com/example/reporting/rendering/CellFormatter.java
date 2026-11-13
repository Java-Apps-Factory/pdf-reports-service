package com.example.reporting.rendering;

/**
 * Turns one row value into display text (Strategy). Instances may hold non-thread-safe formatters, so each
 * document uses its own.
 */
public interface CellFormatter {

    /**
     * Formats a value.
     *
     * @param value the value; {@code null} gives an empty cell
     * @return display text, never {@code null}
     */
    String format(Object value);
}
