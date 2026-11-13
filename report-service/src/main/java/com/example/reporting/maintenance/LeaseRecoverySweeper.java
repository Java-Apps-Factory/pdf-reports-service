package com.example.reporting.maintenance;

import com.example.reporting.observability.ReportingMetrics;
import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.tracking.Backoff;
import com.example.reporting.tracking.DbClock;
import com.example.reporting.tracking.DocumentClaimRepository;
import com.example.reporting.tracking.DocumentClaimRepository.ExpiredLease;
import com.example.reporting.tracking.DocumentStatus;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Resolves expired leases (SPEC section 9, NFR-04), on one node at a time (ShedLock {@value #LOCK_NAME}).
 * GENERATING becomes GENERATION_FAILED with backoff, or GENERATION_FAILED_FINAL when the attempt that died
 * was the last one. SENDING becomes SEND_FAILED, due at once; the message may have been sent, so a duplicate
 * is possible and allowed (NFR-08).
 */
@Component
public class LeaseRecoverySweeper implements SchedulingConfigurer {

    static final String LOCK_NAME = "lease-recovery";

    private static final Logger LOG = LoggerFactory.getLogger(LeaseRecoverySweeper.class);
    private static final Duration LOCK_AT_MOST = Duration.ofMinutes(5);
    private static final Duration LOCK_AT_LEAST = Duration.ofSeconds(5);

    private final DocumentClaimRepository claims;
    private final DbClock dbClock;
    private final LockingTaskExecutor locks;
    private final Backoff backoff;
    private final int maxGenerationAttempts;
    private final Duration interval;
    private final ReportingMetrics metrics;

    /**
     * Creates the sweeper.
     *
     * @param claims claim repository
     * @param dbClock database clock
     * @param locks ShedLock executor
     * @param properties reporting configuration
     * @param metrics meters
     */
    public LeaseRecoverySweeper(DocumentClaimRepository claims, DbClock dbClock, LockingTaskExecutor locks,
            ReportingProperties properties, ReportingMetrics metrics) {
        this.claims = claims;
        this.dbClock = dbClock;
        this.locks = locks;
        ReportingProperties.BackoffSettings settings = properties.getGeneration().getBackoff();
        this.backoff = new Backoff(settings.getInitial(), settings.getMultiplier(), settings.getMax());
        this.maxGenerationAttempts = properties.getGeneration().getMaxAttempts();
        this.interval = properties.getJobs().getLeaseSweepInterval();
        this.metrics = metrics;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::runLocked, interval.toMillis());
    }

    /**
     * Runs one sweep.
     *
     * @return number of documents recovered
     */
    public int sweep() {
        Instant now = dbClock.now();
        int recovered = 0;
        for (ExpiredLease lease : claims.findExpiredLeases(now)) {
            DocumentStatus target;
            Instant nextAttempt = null;
            if (lease.status() == DocumentStatus.SENDING) {
                target = DocumentStatus.SEND_FAILED;
                nextAttempt = now;
            } else if (lease.generationAttempts() >= maxGenerationAttempts) {
                target = DocumentStatus.GENERATION_FAILED_FINAL;
            } else {
                target = DocumentStatus.GENERATION_FAILED;
                nextAttempt = now.plus(backoff.delay(Math.max(1, lease.generationAttempts())));
            }
            String error = "Lease of node " + lease.owner() + " expired while " + lease.status();
            if (claims.recoverLease(lease, target, nextAttempt, error, now)) {
                recovered++;
                metrics.leaseRecovered(lease.status() == DocumentStatus.SENDING ? "send" : "generation");
                LOG.warn("Recovered expired lease of document {} held by {}: {} -> {}", lease.docId(), lease.owner(),
                        lease.status(), target);
            }
        }
        return recovered;
    }

    private void runLocked() {
        try {
            locks.executeWithLock((Runnable) this::sweepSafely,
                    new LockConfiguration(ClockProvider.now(), LOCK_NAME, LOCK_AT_MOST, LOCK_AT_LEAST));
        } catch (RuntimeException e) {
            LOG.error("Could not take lock {}; the next run continues", LOCK_NAME, e);
        }
    }

    private void sweepSafely() {
        try {
            sweep();
        } catch (RuntimeException e) {
            LOG.error("Lease recovery sweep failed; the next sweep continues", e);
        }
    }
}
