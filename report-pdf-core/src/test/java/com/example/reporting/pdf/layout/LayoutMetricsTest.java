package com.example.reporting.pdf.layout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LayoutMetricsTest {

    @Test
    void rowHeightIsLinesTimesLeadingPlusVerticalPadding() {
        assertThat(LayoutMetrics.rowHeight(1)).isEqualTo(11f + 4f);
        assertThat(LayoutMetrics.rowHeight(3)).isEqualTo(33f + 4f);
    }

    @Test
    void summaryReservationIsHeadingPlusRowsPlusHeaderAndTotal() {
        float heading = LayoutMetrics.HEADING_LEADING + LayoutMetrics.HEADING_SPACE_BELOW;
        assertThat(LayoutMetrics.summaryHeight(4)).isEqualTo(heading + 6 * LayoutMetrics.rowHeight(1));
        assertThat(LayoutMetrics.summaryHeight(0)).isEqualTo(heading + 2 * LayoutMetrics.rowHeight(1));
    }

    @Test
    void headingBlockDropsTheSpaceAboveAtThePageTop() {
        assertThat(LayoutMetrics.headingBlockHeight(false) - LayoutMetrics.headingBlockHeight(true))
                .isEqualTo(LayoutMetrics.HEADING_SPACE_ABOVE);
        assertThat(LayoutMetrics.continuationHeight()).isEqualTo(17f);
        assertThat(LayoutMetrics.emptyMessageHeight()).isEqualTo(15f);
    }

    @Test
    void baselineSitsInsideTheLineBox() {
        float baseline = LayoutMetrics.baseline(100f, 11f, 9f);
        assertThat(baseline).isLessThan(100f - 9f * 0.7f).isGreaterThan(100f - 11f);
    }
}
