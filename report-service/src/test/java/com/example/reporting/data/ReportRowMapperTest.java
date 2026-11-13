package com.example.reporting.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReportRowMapperTest {

    private final ResultSet rs = mock(ResultSet.class);

    @Test
    void mapsEveryColumn() throws SQLException {
        stub(2, false, "CAT2 ", Date.valueOf("2026-03-04"), new BigDecimal("-12.50"));
        ReportRow row = new ReportRowMapper().mapRow(rs, 0);

        assertThat(row.displayOrder()).contains(2);
        assertThat(row.categoryCode()).isEqualTo("CAT2");
        assertThat(row.value(ReportRowMapper.CASE_NUMBER)).contains("VA-00000001");
        assertThat(row.value(ReportRowMapper.LAST_NAME)).contains("Testerson");
        assertThat(row.value(ReportRowMapper.FIRST_NAME)).contains("Alpha");
        assertThat(row.value(ReportRowMapper.EVENT_DATE)).contains(LocalDate.of(2026, 3, 4));
        assertThat(row.value(ReportRowMapper.AMOUNT)).contains(new BigDecimal("-12.50"));
        assertThat(row.value(ReportRowMapper.STATUS)).contains("OPEN");
        assertThat(row.value(ReportRowMapper.DESCRIPTION)).contains("Sample record.");
    }

    @Test
    void nullDisplayOrderMeansUnknownCategoryAndNullValuesAreEmpty() throws SQLException {
        stub(0, true, "CATX", null, null);
        ReportRow row = new ReportRowMapper().mapRow(rs, 0);

        assertThat(row.displayOrder()).isEmpty();
        assertThat(row.value(ReportRowMapper.EVENT_DATE)).isEmpty();
        assertThat(row.value(ReportRowMapper.AMOUNT)).isEmpty();
    }

    @Test
    void neverPrintsRecordValuesAndRejectsUnknownKeys() throws SQLException {
        stub(1, false, "CAT1", null, null);
        ReportRow row = new ReportRowMapper().mapRow(rs, 0);
        assertThat(row.toString()).doesNotContain("Testerson", "Alpha", "VA-00000001").contains("CAT1");
        assertThatThrownBy(() -> row.value("ssn")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ReportRowMapper.keys()).containsExactly("caseNumber", "lastName", "firstName", "eventDate",
                "amount", "status", "description");
    }

    @Test
    void workloadRowsValidateCounts() {
        FipsWorkload workload = FipsWorkload.builder().fipsCode("51059").recordCount(3).unknownFips(true).build();
        assertThat(workload.fipsCode()).isEqualTo("51059");
        assertThat(workload.recordCount()).isEqualTo(3);
        assertThat(workload.unknownFips()).isTrue();
        assertThatThrownBy(() -> FipsWorkload.builder().fipsCode("1").recordCount(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void stub(int order, boolean orderNull, String category, Date date, BigDecimal amount)
            throws SQLException {
        when(rs.getInt("DISPLAY_ORDER")).thenReturn(order);
        when(rs.wasNull()).thenReturn(orderNull);
        when(rs.getString("CATEGORY_CODE")).thenReturn(category);
        when(rs.getString("CASE_NUMBER")).thenReturn("VA-00000001");
        when(rs.getString("LAST_NAME")).thenReturn("Testerson");
        when(rs.getString("FIRST_NAME")).thenReturn("Alpha");
        when(rs.getDate("EVENT_DATE")).thenReturn(date);
        when(rs.getBigDecimal("AMOUNT")).thenReturn(amount);
        when(rs.getString("STATUS")).thenReturn("OPEN");
        when(rs.getString("DESCRIPTION")).thenReturn("Sample record.");
    }
}
