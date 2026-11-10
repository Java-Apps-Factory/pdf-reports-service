package com.example.reporting.pdf.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reporting.pdf.layout.LayoutMetrics;
import com.example.reporting.pdf.text.FakeMeasurer;
import com.example.reporting.pdf.text.TextWrapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Row layout as pure math. With the fake measurer at the 9 pt table size every character is 4.5 pt wide;
 * a 51 pt column leaves 45 pt for text (3 pt padding each side), i.e. 10 characters per line.
 */
class RowLayoutTest {

    private static final float[] WIDTHS = {51f, 51f};
    private final FakeMeasurer measurer = new FakeMeasurer();
    private final TableSpec table = TableSpec.builder()
            .addColumn(ColumnSpec.builder("Wraps", 1).build())
            .addColumn(ColumnSpec.builder("No wrap", 1).alignment(Alignment.RIGHT).wrap(false).build())
            .build();

    @Test
    void rowHeightComesFromTheTallestWrappedCell() {
        RowLayout row = RowLayout.of(table, cells("alpha beta gamma delta", "x"), WIDTHS, measurer);
        assertThat(row.lines(0)).containsExactly("alpha beta", "gamma", "delta");
        assertThat(row.lineCount()).isEqualTo(3);
        assertThat(row.height()).isEqualTo(3 * LayoutMetrics.TABLE_LEADING + 2 * LayoutMetrics.CELL_PADDING_Y);
        assertThat(row.isTruncated()).isFalse();
    }

    @Test
    void singleLineRowsHaveTheMinimumHeight() {
        RowLayout row = RowLayout.of(table, cells("", ""), WIDTHS, measurer);
        assertThat(row.height()).isEqualTo(LayoutMetrics.rowHeight(1));
    }

    @Test
    void noWrapColumnsAreCutWithAnEllipsis() {
        RowLayout row = RowLayout.of(table, cells("a", "abcdefghijklmnop"), WIDTHS, measurer);
        assertThat(row.lines(1)).containsExactly("abcdefghi" + TextWrapper.ELLIPSIS);
        assertThat(row.lineCount()).isEqualTo(1);
    }

    @Test
    void cellAlignmentOverridesTheColumn() {
        RowLayout row = RowLayout.of(table, List.of(CellValue.of("a", Alignment.CENTER), CellValue.of("b")),
                WIDTHS, measurer);
        assertThat(row.alignment(0)).isEqualTo(Alignment.CENTER);
        assertThat(row.alignment(1)).isEqualTo(Alignment.RIGHT);
        assertThat(row.cellCount()).isEqualTo(2);
    }

    @Test
    void oversizedRowsAreCutToFitAndEndInAnEllipsis() {
        String longText = "word ".repeat(200);
        RowLayout row = RowLayout.of(table, cells(longText, "x"), WIDTHS, measurer);
        assertThat(row.lineCount()).isEqualTo(100);

        RowLayout cut = row.fitTo(LayoutMetrics.rowHeight(5) + 3f);
        assertThat(cut.isTruncated()).isTrue();
        assertThat(cut.lineCount()).isEqualTo(5);
        assertThat(cut.height()).isEqualTo(LayoutMetrics.rowHeight(5));
        assertThat(cut.lines(0).get(4)).endsWith(TextWrapper.ELLIPSIS);
        assertThat(cut.lines(1)).containsExactly("x");
    }

    @Test
    void fitToKeepsRowsThatFitAndAlwaysKeepsOneLine() {
        RowLayout row = RowLayout.of(table, cells("alpha beta gamma", "x"), WIDTHS, measurer);
        assertThat(row.fitTo(1000f)).isSameAs(row);
        assertThat(row.fitTo(1f).lineCount()).isEqualTo(1);
    }

    @Test
    void headerLabelsWrapInEveryColumn() {
        RowLayout header = RowLayout.header(table, List.of("Event Date Of Record", "Amount Due Today"), WIDTHS,
                measurer);
        assertThat(header.lines(0)).containsExactly("Event Date", "Of Record");
        assertThat(header.lines(1)).containsExactly("Amount Due", "Today");
        assertThat(header.alignment(1)).isEqualTo(Alignment.RIGHT);
    }

    @Test
    void rejectsWrongCellCount() {
        assertThatThrownBy(() -> RowLayout.of(table, List.of(CellValue.of("a")), WIDTHS, measurer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<CellValue> cells(String first, String second) {
        return List.of(CellValue.of(first), CellValue.of(second));
    }
}
