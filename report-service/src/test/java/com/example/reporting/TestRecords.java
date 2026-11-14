package com.example.reporting;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test helper: bulk-inserts fake Virginia source records (fake names only), spread round-robin over FIPS codes. */
public final class TestRecords {

    private static final String INSERT = "INSERT INTO RPT_RECORD (STATE_CODE, FIPS_CODE, CATEGORY_CODE, "
            + "CASE_NUMBER, LAST_NAME, FIRST_NAME, EVENT_DATE, AMOUNT, STATUS, DESCRIPTION) "
            + "VALUES ('VA', ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String[] LAST = {"Testerson", "Sampleton", "Placeholder", "Mockingham", "Demoville"};
    private static final String[] FIRST = {"Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot"};
    private static final String LONG_TEXT = "Long sample description that wraps over several lines in the description "
            + "column so that row heights vary across the page.";
    private static final int BATCH = 1_000;

    private TestRecords() {
    }

    /**
     * Inserts {@code count} records.
     *
     * @param jdbc JdbcTemplate of the database under test
     * @param fipsCodes FIPS codes to spread the records over
     * @param count number of records
     */
    public static void insert(JdbcTemplate jdbc, List<String> fipsCodes, int count) {
        List<Object[]> batch = new ArrayList<>(BATCH);
        LocalDate base = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < count; i++) {
            String fips = fipsCodes.get(i % fipsCodes.size());
            batch.add(new Object[] {fips, "CAT" + (1 + i % 4), String.format("VA-%08d", i), LAST[i % LAST.length],
                    FIRST[(i / 7) % FIRST.length], Date.valueOf(base.minusDays(i % 1_000)),
                    BigDecimal.valueOf(i % 500_000, 2), i % 3 == 0 ? "OPEN" : "CLOSED",
                    i % 5 == 0 ? LONG_TEXT : "Sample record."});
            if (batch.size() == BATCH) {
                jdbc.batchUpdate(INSERT, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(INSERT, batch);
        }
    }
}
