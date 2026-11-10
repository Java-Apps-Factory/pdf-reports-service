package com.example.reporting.dev;

import com.example.reporting.profile.ReportingProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fills the development source table with fake records (SPEC section 7, "Development data").
 *
 * <p>Active only in the {@code h2} profile. Inserts {@code reporting.dev.seed-records} rows when
 * RPT_RECORD is empty and does nothing otherwise, so restarts never duplicate data. Rows are
 * written in batches, so the 100,000-record performance run can reuse it with a flat heap.
 */
@Component
@Profile("h2")
public final class DevDataSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final long SEED = 42L;
    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final ReportingProperties properties;
    private final Clock clock;

    /**
     * Creates the seeder.
     *
     * @param jdbcTemplate access to the development database
     * @param properties reporting configuration
     * @param clock application clock, used as the base for fake event dates
     */
    public DevDataSeeder(JdbcTemplate jdbcTemplate, ReportingProperties properties, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        int target = properties.getDev().getSeedRecords();
        if (target <= 0) {
            LOG.info("Dev seeding disabled (reporting.dev.seed-records={})", target);
            return;
        }
        Long existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM RPT_RECORD", Long.class);
        if (existing != null && existing > 0) {
            LOG.info("Dev seeding skipped: RPT_RECORD already holds {} records", existing);
            return;
        }
        seed(target);
        LOG.info("Dev seeding inserted {} records into RPT_RECORD", target);
    }

    private void seed(int target) {
        DevRecordGenerator generator = new DevRecordGenerator(SEED, LocalDate.now(clock));
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        for (int i = 0; i < target; i++) {
            batch.add(generator.next());
            if (batch.size() == BATCH_SIZE) {
                flush(batch);
            }
        }
        flush(batch);
    }

    private void flush(List<Object[]> batch) {
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(DevRecordGenerator.insertSql(), batch);
            batch.clear();
        }
    }
}
