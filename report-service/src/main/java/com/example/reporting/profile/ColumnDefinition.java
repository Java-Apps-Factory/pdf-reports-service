package com.example.reporting.profile;

import java.util.Objects;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConstructorBinding;

/**
 * One configured report column: row field key, header label, type, width (percent of the table) and
 * wrapping. Bound from {@code reporting.states.STATE.columns}.
 */
public final class ColumnDefinition {

    @NotBlank
    private final String key;

    @NotBlank
    private final String label;

    @NotNull
    private final ColumnType type;

    @Min(1)
    @Max(100)
    private final int width;

    private final Boolean wrap;

    /**
     * Creates the column; called by Spring Boot's binder.
     *
     * @param key row field produced by {@code ReportRowMapper}
     * @param label header label
     * @param type value type
     * @param width width in percent; a state's widths sum to 100
     * @param wrap whether text wraps; {@code null} means wrap for TEXT. Ignored for other types.
     */
    @ConstructorBinding
    public ColumnDefinition(String key, String label, ColumnType type, int width, Boolean wrap) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.width = width;
        this.wrap = wrap;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public ColumnType getType() {
        return type;
    }

    public int getWidth() {
        return width;
    }

    /**
     * Returns whether the column's text wraps: only TEXT columns wrap, and only unless configured off.
     *
     * @return effective wrap flag
     */
    public boolean isWrap() {
        return type != null && type.canWrap() && !Boolean.FALSE.equals(wrap);
    }

    @Override
    public String toString() {
        return "ColumnDefinition[" + key + ", " + type + ", " + width + "%]";
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ColumnDefinition)) {
            return false;
        }
        ColumnDefinition that = (ColumnDefinition) other;
        return width == that.width && Objects.equals(key, that.key) && Objects.equals(label, that.label)
                && type == that.type && Objects.equals(wrap, that.wrap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, label, type, width, wrap);
    }
}
