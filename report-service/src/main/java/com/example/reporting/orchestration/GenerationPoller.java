package com.example.reporting.orchestration;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Claims PENDING and due GENERATION_FAILED documents up to the number of free generation threads and hands
 * each to a {@link GenerationWorker} (SPEC section 15). Runs on every node without ShedLock: the claim UPDATE
 * already serializes nodes.
 */
@Component
public class GenerationPoller implements SchedulingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationPoller.class);

    private final DocumentClaimRepository claims;
    private final DbClock dbClock;
    private final GenerationWorker worker;
    private final TaskExecutor executor;
    private final Semaphore freeWorkers;
    private final AtomicLong claimedTotal = new AtomicLong();
    private final String nodeId;
    private final Duration lease;
    private final Duration interval;

    /**
     * Creates the poller.
     *
     * @param claims claim repository
     * @param dbClock database clock
     * @param worker generation worker
     * @param executor generation thread pool (queue capacity 0)
     * @param properties reporting configuration
     */
    public GenerationPoller(DocumentClaimRepository claims, DbClock dbClock, GenerationWorker worker,
            @Qualifier("generationExecutor") TaskExecutor executor, ReportingProperties properties) {
        this.claims = claims;
        this.dbClock = dbClock;
        this.worker = worker;
        this.executor = executor;
        this.freeWorkers = new Semaphore(properties.getGeneration().getWorkers());
        this.nodeId = properties.getNodeId();
        this.lease = properties.getGeneration().getLeaseDuration();
        this.interval = properties.getGeneration().getPollInterval();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::pollSafely, interval.toMillis());
    }

    /**
     * Runs one poll: claims as many documents as there are free worker threads and starts them.
     *
     * @return number of documents claimed
     */
    public int poll() {
        int free = freeWorkers.availablePermits();
        if (free == 0) {
            return 0;
        }
        Instant now = dbClock.now();
        List<Claim> won = claims.claimForGeneration(free, nodeId, now, now.plus(lease));
        for (Claim claim : won) {
            freeWorkers.acquireUninterruptibly();
            try {
                executor.execute(() -> {
                    try {
                        worker.generate(claim);
                    } finally {
                        freeWorkers.release();
                    }
                });
            } catch (TaskRejectedException e) {
                freeWorkers.release();
                LOG.warn("Generation pool rejected document {}; its lease will expire and be recovered",
                        claim.docId());
            }
        }
        claimedTotal.addAndGet(won.size());
        return won.size();
    }

    /**
     * Returns how many documents this node has claimed since startup.
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
            LOG.error("Generation poll failed; the next poll continues", e);
        }
    }
}
