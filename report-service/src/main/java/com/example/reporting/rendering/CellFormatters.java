package com.example.reporting.rendering;

import com.example.reporting.profile.ColumnType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

/**
 * Formatters for each {@link ColumnType} (SPEC section 11): TEXT as stored and trimmed, DATE as
 * {@code MM/dd/yyyy}, CURRENCY as {@code $#,##0.00} with negatives as {@code -$1,234.00}, INTEGER as
 * {@code #,##0}. Null values become empty cells. Rounding is half-up.
 */
public final class CellFormatters {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US);

    private CellFormatters() {
    }

    /**
     * Returns a new formatter for a column type. Not thread-safe; create one per document.
     *
     * @param type column type
     * @return formatter
     */
    public static CellFormatter forType(ColumnType type) {
        switch (type) {
            case DATE:
                return CellFormatters::date;
            case CURRENCY:
                return decimal("$#,##0.00;-$#,##0.00", 2);
            case INTEGER:
                return decimal("#,##0;-#,##0", 0);
            case TEXT:
            default:
                return value -> value == null ? "" : value.toString().strip();
        }
    }

    private static String date(Object value) {
        if (value == null) {
            return "";
        }
        if (!(value instanceof TemporalAccessor)) {
            throw new IllegalArgumentException("DATE column needs a date, got " + value.getClass().getSimpleName());
        }
        return DATE.format((TemporalAccessor) value);
    }

    private static CellFormatter decimal(String pattern, int scale) {
        DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return value -> {
            if (value == null) {
                return "";
            }
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("Numeric column needs a number, got "
                        + value.getClass().getSimpleName());
            }
            BigDecimal number = new BigDecimal(value.toString()).setScale(scale, RoundingMode.HALF_UP);
            // Avoid "-$0.00" for tiny negative values that round to zero.
            return format.format(number.signum() == 0 ? number.abs() : number);
        };
    }
}
