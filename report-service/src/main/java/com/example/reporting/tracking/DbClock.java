package com.example.reporting.tracking;

import com.example.reporting.profile.ReportingProperties;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The database clock (AD-03). Lease, attempt and run times all come from here, never from node clocks, so
 * clock drift between nodes cannot cause double processing. The vendor query is configured in
 * {@code reporting.db.now-query}.
 */
@Component
public class DbClock {

    private final JdbcTemplate jdbc;
    private final String nowQuery;

    /**
     * Creates the clock.
     *
     * @param jdbc application JdbcTemplate
     * @param properties reporting configuration
     */
    public DbClock(JdbcTemplate jdbc, ReportingProperties properties) {
        this.jdbc = jdbc;
        this.nowQuery = properties.getDb().getNowQuery();
    }

    /**
     * Returns the current database time.
     *
     * @return database time as an instant (read through the JVM time zone, like every other timestamp)
     */
    public Instant now() {
        Timestamp now = jdbc.queryForObject(nowQuery, Timestamp.class);
        if (now == null) {
            throw new IllegalStateException("Database returned no time for " + nowQuery);
        }
        return now.toInstant();
    }
}
