package com.example.reporting.data;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps one row of {@code sql/report-rows.sql} to a {@link ReportRow}. Integrating the real source table
 * means changing this class, the two report SQL files and the column profile only (SPEC section 22).
 */
public final class ReportRowMapper implements RowMapper<ReportRow> {

    /** Column key of the case number. */
    public static final String CASE_NUMBER = "caseNumber";
    /** Column key of the last name. */
    public static final String LAST_NAME = "lastName";
    /** Column key of the first name. */
    public static final String FIRST_NAME = "firstName";
    /** Column key of the event date. */
    public static final String EVENT_DATE = "eventDate";
    /** Column key of the amount. */
    public static final String AMOUNT = "amount";
    /** Column key of the status. */
    public static final String STATUS = "status";
    /** Column key of the description. */
    public static final String DESCRIPTION = "description";

    /** Every column key this mapper produces, with the Java type of its values. */
    public static final Map<String, Class<?>> FIELD_TYPES = fieldTypes();

    @Override
    public ReportRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        int order = rs.getInt("DISPLAY_ORDER");
        Integer displayOrder = rs.wasNull() ? null : order;
        Date eventDate = rs.getDate("EVENT_DATE");
        return ReportRow.builder()
                .displayOrder(displayOrder)
                .categoryCode(trim(rs.getString("CATEGORY_CODE")))
                .caseNumber(rs.getString("CASE_NUMBER"))
                .lastName(rs.getString("LAST_NAME"))
                .firstName(rs.getString("FIRST_NAME"))
                .eventDate(eventDate == null ? null : eventDate.toLocalDate())
                .amount(rs.getBigDecimal("AMOUNT"))
                .status(rs.getString("STATUS"))
                .description(rs.getString("DESCRIPTION"))
                .build();
    }

    /**
     * Returns the keys this mapper produces.
     *
     * @return unmodifiable key set
     */
    public static Set<String> keys() {
        return FIELD_TYPES.keySet();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static Map<String, Class<?>> fieldTypes() {
        Map<String, Class<?>> types = new LinkedHashMap<>();
        types.put(CASE_NUMBER, String.class);
        types.put(LAST_NAME, String.class);
        types.put(FIRST_NAME, String.class);
        types.put(EVENT_DATE, LocalDate.class);
        types.put(AMOUNT, BigDecimal.class);
        types.put(STATUS, String.class);
        types.put(DESCRIPTION, String.class);
        return Collections.unmodifiableMap(types);
    }
}
