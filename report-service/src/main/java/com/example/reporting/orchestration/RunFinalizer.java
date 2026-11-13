package com.example.reporting.orchestration;

import com.example.reporting.observability.ReportingMetrics;
import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.tracking.DbClock;
import com.example.reporting.tracking.DocumentStatus;
import com.example.reporting.tracking.ReportDocumentRepository;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.ReportRunRepository;
import com.example.reporting.tracking.RunStatus;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves runs forward (SPEC section 8, run completion), on one node at a time (ShedLock {@value #LOCK_NAME}):
 * RUNNING → DISPATCHING when no document is PENDING, GENERATING or GENERATION_FAILED, releasing the state lock
 * in the same transaction; DISPATCHING → COMPLETED or COMPLETED_WITH_ERRORS when every document is final.
 */
@Component
public class RunFinalizer implements SchedulingConfigurer {

    static final String LOCK_NAME = "run-finalizer";

    private static final Logger LOG = LoggerFactory.getLogger(RunFinalizer.class);
    private static final Set<DocumentStatus> GENERATION_OPEN =
            EnumSet.of(DocumentStatus.PENDING, DocumentStatus.GENERATING, DocumentStatus.GENERATION_FAILED);
    private static final Set<DocumentStatus> NOT_FINAL = EnumSet.complementOf(
            EnumSet.copyOf(DocumentStatus.finalStatuses()));
    private static final Set<DocumentStatus> FAILED = EnumSet.of(DocumentStatus.GENERATION_FAILED_FINAL,
            DocumentStatus.DATA_ERROR, DocumentStatus.SEND_FAILED_FINAL);
    private static final Duration LOCK_AT_MOST = Duration.ofMinutes(5);
    private static final Duration LOCK_AT_LEAST = Duration.ofSeconds(5);

    private final ReportRunRepository runs;
    private final ReportDocumentRepository documents;
    private final RunStateLock stateLock;
    private final DbClock dbClock;
    private final TransactionTemplate transaction;
    private final LockingTaskExecutor locks;
    private final Duration interval;
    private final ReportingMetrics metrics;

    /**
     * Creates the finalizer.
     *
     * @param runs run repository
     * @param documents document repository
     * @param stateLock per-state run lock
     * @param dbClock database clock
     * @param transactionManager transaction manager
     * @param locks ShedLock executor
     * @param properties reporting configuration
     * @param metrics meters; the per-status gauges are refreshed on each pass
     */
    public RunFinalizer(ReportRunRepository runs, ReportDocumentRepository documents, RunStateLock stateLock,
            DbClock dbClock, PlatformTransactionManager transactionManager, LockingTaskExecutor locks,
            ReportingProperties properties, ReportingMetrics metrics) {
        this.runs = runs;
        this.documents = documents;
        this.stateLock = stateLock;
        this.dbClock = dbClock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.locks = locks;
        this.interval = properties.getJobs().getFinalizerInterval();
        this.metrics = metrics;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::runLocked, interval.toMillis());
    }

    /**
     * Runs one pass over RUNNING and DISPATCHING runs.
     *
     * @return number of runs whose status changed
     */
    public int finalizeRuns() {
        int changed = 0;
        for (ReportRun run : runs.findByStatusOrderByRunIdAsc(RunStatus.RUNNING)) {
            if (documents.countByRunIdAndStatusIn(run.getRunId(), GENERATION_OPEN) == 0) {
                changed += transition(run.getRunId(), RunStatus.RUNNING, RunStatus.DISPATCHING);
            }
        }
        for (ReportRun run : runs.findByStatusOrderByRunIdAsc(RunStatus.DISPATCHING)) {
            if (documents.countByRunIdAndStatusIn(run.getRunId(), NOT_FINAL) == 0) {
                boolean failed = documents.countByRunIdAndStatusIn(run.getRunId(), FAILED) > 0;
                changed += transition(run.getRunId(), RunStatus.DISPATCHING,
                        failed ? RunStatus.COMPLETED_WITH_ERRORS : RunStatus.COMPLETED);
            }
        }
        Map<DocumentStatus, Long> counts = new EnumMap<>(DocumentStatus.class);
        documents.countAllByStatus().forEach(c -> counts.put(c.getStatus(), c.getCount()));
        metrics.documentsByStatus(counts);
        return changed;
    }

    private int transition(long runId, RunStatus from, RunStatus to) {
        Integer result = transaction.execute(status -> {
            ReportRun run = runs.findById(runId).orElseThrow();
            if (run.getStatus() != from) {
                return 0;
            }
            run.moveTo(to, dbClock.now());
            if (from == RunStatus.RUNNING) {
                stateLock.release(run.getStateCode(), runId);
            }
            runs.saveAndFlush(run);
            return 1;
        });
        if (result != null && result == 1) {
            Map<DocumentStatus, Long> byStatus = new EnumMap<>(DocumentStatus.class);
            documents.countByStatus(runId).forEach(c -> byStatus.put(c.getStatus(), c.getCount()));
            LOG.info("Run {} moved {} -> {}; documents {}", runId, from, to, byStatus);
            return 1;
        }
        return 0;
    }

    private void runLocked() {
        try {
            locks.executeWithLock((Runnable) this::finalizeSafely,
                    new LockConfiguration(ClockProvider.now(), LOCK_NAME, LOCK_AT_MOST, LOCK_AT_LEAST));
        } catch (RuntimeException e) {
            LOG.error("Could not take lock {}; the next run continues", LOCK_NAME, e);
        }
    }

    private void finalizeSafely() {
        try {
            finalizeRuns();
        } catch (RuntimeException e) {
            LOG.error("Run finalizer pass failed; the next pass continues", e);
        }
    }
}
