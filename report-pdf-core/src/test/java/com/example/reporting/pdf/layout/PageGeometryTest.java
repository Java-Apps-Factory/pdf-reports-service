package com.example.reporting.pdf.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageGeometryTest {

    @Test
    void defaultsAreUsLetterLandscapeWithHalfInchMargins() {
        PageGeometry geometry = PageGeometry.letterLandscape();
        assertThat(geometry.width()).isEqualTo(792f);
        assertThat(geometry.height()).isEqualTo(612f);
        assertThat(geometry.margins().top()).isEqualTo(36f);
        assertThat(geometry.margins().left()).isEqualTo(36f);
        assertThat(geometry.contentLeft()).isEqualTo(36f);
        assertThat(geometry.contentWidth()).isEqualTo(720f);
    }

    @Test
    void bodyAreaIsPageMinusMarginsHeaderRegionAndFooterRegion() {
        PageGeometry geometry = PageGeometry.letterLandscape();
        // Page 1: the title block (96 pt) is the header region.
        assertThat(geometry.bodyHeight(1)).isEqualTo(612f - 36f - 36f - 96f - 28f);
        assertThat(geometry.bodyTop(1)).isEqualTo(612f - 36f - 96f);
        // Pages 2+: the running header (28 pt) is the header region.
        assertThat(geometry.bodyHeight(2)).isEqualTo(612f - 36f - 36f - 28f - 28f);
        assertThat(geometry.bodyHeight(7)).isEqualTo(geometry.bodyHeight(2));
        assertThat(geometry.bodyBottom()).isEqualTo(36f + 28f);
        assertThat(geometry.headerHeight(1)).isEqualTo(96f);
        assertThat(geometry.headerHeight(2)).isEqualTo(28f);
    }

    @Test
    void builderOverridesEveryDimension() {
        PageGeometry geometry = PageGeometry.builder()
                .pageSize(612f, 792f)
                .margins(Margins.of(10f, 20f, 30f, 40f))
                .titleBlockHeight(80f)
                .runningHeaderHeight(20f)
                .footerHeight(24f)
                .build();
        assertThat(geometry.contentWidth()).isEqualTo(612f - 20f - 40f);
        assertThat(geometry.bodyHeight(1)).isEqualTo(792f - 10f - 30f - 80f - 24f);
        assertThat(geometry.runningHeaderHeight()).isEqualTo(20f);
        assertThat(geometry.titleBlockHeight()).isEqualTo(80f);
        assertThat(geometry.footerHeight()).isEqualTo(24f);
        assertThat(geometry.margins().right()).isEqualTo(20f);
        assertThat(geometry.margins().bottom()).isEqualTo(30f);
    }

    @Test
    void rejectsGeometryWithoutBody() {
        assertThatThrownBy(() -> PageGeometry.builder().titleBlockHeight(600f).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageGeometry.builder().footerHeight(0f).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Margins.uniform(-1f)).isInstanceOf(IllegalArgumentException.class);
    }
}
