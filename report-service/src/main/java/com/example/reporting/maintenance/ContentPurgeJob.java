package com.example.reporting.maintenance;

import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.tracking.DbClock;
import com.example.reporting.tracking.ReportContentStore;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes stored PDFs {@code retention.content-days} (90) after they were sent (FR-18), 500 documents per
 * transaction, and sets {@code CONTENT_PURGED_AT}; tracking metadata is kept. SUPERSEDED documents, which are
 * never sent, are purged the same number of days after they were superseded. One node at a time (ShedLock
 * {@value #LOCK_NAME}).
 */
@Component
public class ContentPurgeJob implements SchedulingConfigurer {

    static final String LOCK_NAME = "content-purge";
    static final int BATCH = 500;

    private static final Logger LOG = LoggerFactory.getLogger(ContentPurgeJob.class);
    private static final Duration LOCK_AT_MOST = Duration.ofHours(1);
    private static final Duration LOCK_AT_LEAST = Duration.ofMinutes(1);

    private final ReportContentStore content;
    private final DbClock dbClock;
    private final TransactionTemplate transaction;
    private final LockingTaskExecutor locks;
    private final Duration retention;
    private final String cron;
    private final ZoneId zone;

    /**
     * Creates the job.
     *
     * @param content PDF store
     * @param dbClock database clock
     * @param transactionManager transaction manager
     * @param locks ShedLock executor
     * @param properties reporting configuration
     */
    public ContentPurgeJob(ReportContentStore content, DbClock dbClock, PlatformTransactionManager transactionManager,
            LockingTaskExecutor locks, ReportingProperties properties) {
        this.content = content;
        this.dbClock = dbClock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.locks = locks;
        this.retention = Duration.ofDays(properties.getRetention().getContentDays());
        this.cron = properties.getRetention().getPurgeCron();
        this.zone = properties.getBusinessZone();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(new CronTask(this::purgeLocked, new CronTrigger(cron, zone)));
    }

    /**
     * Purges every eligible document, batch by batch.
     *
     * @return number of documents purged
     */
    public int purge() {
        Instant now = dbClock.now();
        Instant cutoff = now.minus(retention);
        int total = 0;
        while (true) {
            List<Long> batch = content.findPurgeable(cutoff, BATCH);
            if (batch.isEmpty()) {
                break;
            }
            transaction.executeWithoutResult(status -> content.purge(batch, now));
            total += batch.size();
            if (batch.size() < BATCH) {
                break;
            }
        }
        if (total > 0) {
            LOG.info("Purged the stored PDFs of {} documents", total);
        }
        return total;
    }

    private void purgeLocked() {
        try {
            locks.executeWithLock((Runnable) this::purgeSafely,
                    new LockConfiguration(ClockProvider.now(), LOCK_NAME, LOCK_AT_MOST, LOCK_AT_LEAST));
        } catch (RuntimeException e) {
            LOG.error("Could not take lock {}; the next run continues", LOCK_NAME, e);
        }
    }

    private void purgeSafely() {
        try {
            purge();
        } catch (RuntimeException e) {
            LOG.error("Content purge failed; the next run continues", e);
        }
    }
}
