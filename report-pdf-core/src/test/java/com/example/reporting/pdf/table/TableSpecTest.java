package com.example.reporting.pdf.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.example.reporting.pdf.TestReports;
import org.junit.jupiter.api.Test;

class TableSpecTest {

    @Test
    void columnWidthsFollowTheRatios() {
        float[] widths = TestReports.virginiaTable().columnWidths(720f);
        assertThat(widths).hasSize(7);
        assertThat(widths[0]).isCloseTo(720f * 0.14f, within(0.01f));
        assertThat(widths[6]).isCloseTo(720f * 0.27f, within(0.01f));
        float sum = 0f;
        for (float w : widths) {
            sum += w;
        }
        assertThat(sum).isCloseTo(720f, within(0.01f));
    }

    @Test
    void rejectsEmptyTablesAndBadRatios() {
        assertThatThrownBy(() -> TableSpec.builder().build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ColumnSpec.builder("x", 0f).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ColumnSpec.builder(null, 1f).build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void cellValuesCarryOptionalAlignment() {
        assertThat(CellValue.of(null).text()).isEmpty();
        assertThat(CellValue.of("x").alignment()).isEmpty();
        assertThat(CellValue.of("x", Alignment.CENTER).alignment()).contains(Alignment.CENTER);
        assertThat(CellValue.of(null, Alignment.RIGHT).text()).isEmpty();
        assertThat(CellValue.empty().text()).isEmpty();
    }

    @Test
    void alignedXPlacesTextInsideThePadding() {
        assertThat(TableRenderer.alignedX(100f, 50f, 20f, Alignment.LEFT)).isEqualTo(103f);
        assertThat(TableRenderer.alignedX(100f, 50f, 20f, Alignment.RIGHT)).isEqualTo(127f);
        assertThat(TableRenderer.alignedX(100f, 50f, 20f, Alignment.CENTER)).isEqualTo(115f);
    }
}
