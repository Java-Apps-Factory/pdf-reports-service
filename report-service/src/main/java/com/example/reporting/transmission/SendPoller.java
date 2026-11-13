package com.example.reporting.transmission;

import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.tracking.DbClock;
import com.example.reporting.tracking.DocumentClaimRepository;
import com.example.reporting.tracking.DocumentClaimRepository.Claim;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Claims GENERATED and due SEND_FAILED documents up to the free send permits and hands each to a
 * {@link SendWorker} (SPEC section 15). Polls on a fixed delay and is woken after a document is committed as
 * GENERATED (Observer), so delivery starts without waiting for the next poll. Runs on every node without
 * ShedLock: the claim UPDATE serializes nodes.
 */
@Component
public class SendPoller implements SchedulingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(SendPoller.class);

    private final DocumentClaimRepository claims;
    private final DbClock dbClock;
    private final SendWorker worker;
    private final TaskExecutor executor;
    private final ObjectProvider<TaskScheduler> scheduler;
    private final Semaphore permits;
    private final int maxPermits;
    private final AtomicLong claimedTotal = new AtomicLong();
    private final String nodeId;
    private final Duration lease;
    private final Duration interval;
    private final boolean enabled;

    /**
     * Creates the poller.
     *
     * @param claims claim repository
     * @param dbClock database clock
     * @param worker send worker
     * @param executor send thread pool (queue capacity 0)
     * @param scheduler job scheduler used for wake-ups; absent when jobs are disabled
     * @param properties reporting configuration
     */
    public SendPoller(DocumentClaimRepository claims, DbClock dbClock, SendWorker worker,
            @Qualifier("sendExecutor") TaskExecutor executor, ObjectProvider<TaskScheduler> scheduler,
            ReportingProperties properties) {
        this.claims = claims;
        this.dbClock = dbClock;
        this.worker = worker;
        this.executor = executor;
        this.scheduler = scheduler;
        this.maxPermits = properties.getTransmission().getWorkers();
        this.permits = new Semaphore(maxPermits);
        this.nodeId = properties.getNodeId();
        this.lease = properties.getTransmission().getLeaseDuration();
        this.interval = properties.getTransmission().getPollInterval();
        this.enabled = properties.getJobs().isEnabled();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::pollSafely, interval.toMillis());
    }

    /**
     * Wakes the poller once a generated document is committed. The poll runs on the job scheduler, never on the
     * committing thread, whose transaction resources may still be bound.
     *
     * @param event the committed document
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentGenerated(DocumentGeneratedEvent event) {
        if (!enabled) {
            return;
        }
        TaskScheduler jobs = scheduler.getIfAvailable();
        if (jobs != null) {
            jobs.schedule(this::pollSafely, Instant.now());
        }
    }

    /**
     * Runs one poll: takes the free send permits, claims that many documents and starts them; safe to call
     * from several threads at once.
     *
     * @return number of documents claimed
     */
    public int poll() {
        int taken = 0;
        while (taken < maxPermits && permits.tryAcquire()) {
            taken++;
        }
        if (taken == 0) {
            return 0;
        }
        int started = 0;
        try {
            Instant now = dbClock.now();
            List<Claim> won = claims.claimForSending(taken, nodeId, now, now.plus(lease));
            for (Claim claim : won) {
                try {
                    executor.execute(() -> {
                        try {
                            worker.send(claim);
                        } finally {
                            permits.release();
                        }
                    });
                    started++;
                } catch (TaskRejectedException e) {
                    LOG.warn("Send pool rejected document {}; its lease will expire and be recovered",
                            claim.docId());
                }
            }
            claimedTotal.addAndGet(won.size());
            return won.size();
        } finally {
            permits.release(taken - started);
        }
    }

    /**
     * Returns how many documents this node has claimed for sending since startup.
     *
     * @return claim count
     */
    public long claimedTotal() {
        return claimedTotal.get();
    }

    private void pollSafely() {
        try {
            poll();
        } catch (RuntimeException e) {
            LOG.error("Send poll failed; the next poll continues", e);
        }
    }
}
