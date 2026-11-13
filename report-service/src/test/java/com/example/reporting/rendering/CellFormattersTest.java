package com.example.reporting.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reporting.profile.ColumnType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Value formatting of SPEC section 11. */
class CellFormattersTest {

    @Test
    void textIsTrimmed() {
        CellFormatter text = CellFormatters.forType(ColumnType.TEXT);
        assertThat(text.format("  Testerson  ")).isEqualTo("Testerson");
        assertThat(text.format(42)).isEqualTo("42");
    }

    @Test
    void datesAreMonthDayYear() {
        assertThat(CellFormatters.forType(ColumnType.DATE).format(LocalDate.of(2026, 3, 4))).isEqualTo("03/04/2026");
    }

    @Test
    void currencyHasDollarSignGroupingAndLeadingMinus() {
        CellFormatter currency = CellFormatters.forType(ColumnType.CURRENCY);
        assertThat(currency.format(new BigDecimal("1234"))).isEqualTo("$1,234.00");
        assertThat(currency.format(new BigDecimal("-1234"))).isEqualTo("-$1,234.00");
        assertThat(currency.format(new BigDecimal("0.005"))).isEqualTo("$0.01");
        assertThat(currency.format(new BigDecimal("1234567.891"))).isEqualTo("$1,234,567.89");
        assertThat(currency.format(new BigDecimal("-0.001"))).isEqualTo("$0.00");
        assertThat(currency.format(7)).isEqualTo("$7.00");
    }

    @Test
    void integersHaveGrouping() {
        CellFormatter integer = CellFormatters.forType(ColumnType.INTEGER);
        assertThat(integer.format(1234567L)).isEqualTo("1,234,567");
        assertThat(integer.format(new BigDecimal("2.5"))).isEqualTo("3");
        assertThat(integer.format(-42)).isEqualTo("-42");
    }

    @Test
    void nullsBecomeEmptyCells() {
        for (ColumnType type : ColumnType.values()) {
            assertThat(CellFormatters.forType(type).format(null)).as(type.name()).isEmpty();
        }
    }

    @Test
    void wrongValueTypesAreRejected() {
        assertThatThrownBy(() -> CellFormatters.forType(ColumnType.DATE).format("2026-01-01"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CellFormatters.forType(ColumnType.CURRENCY).format("12"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void columnTypesKnowAlignmentAndWrapping() {
        assertThat(ColumnType.CURRENCY.isRightAligned()).isTrue();
        assertThat(ColumnType.INTEGER.isRightAligned()).isTrue();
        assertThat(ColumnType.DATE.isRightAligned()).isFalse();
        assertThat(ColumnType.TEXT.canWrap()).isTrue();
        assertThat(ColumnType.DATE.canWrap()).isFalse();
        assertThat(ColumnType.INTEGER.accepts(Long.class)).isTrue();
        assertThat(ColumnType.DATE.accepts(String.class)).isFalse();
    }
}
