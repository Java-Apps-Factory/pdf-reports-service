package com.example.reporting.scheduling;

import com.example.reporting.orchestration.ReportRunService;
import com.example.reporting.orchestration.RunAlreadyActiveException;
import com.example.reporting.orchestration.StartRunCommand;
import com.example.reporting.profile.ProfileRegistry;
import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.profile.StateProfile;
import com.example.reporting.tracking.TriggerType;
import java.time.Duration;
import java.time.ZoneId;
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

/**
 * Registers one cron task per enabled state (FR-09, SPEC section 15). Each run goes through ShedLock lock
 * {@code daily-report-{STATE}} (at most 30 min, at least 1 min) so only one node starts it. An active run is
 * logged at WARN, never raised.
 */
@Component
public class DailyReportScheduler implements SchedulingConfigurer {

    /** Requester recorded on scheduled runs. */
    public static final String REQUESTER = "scheduler";

    private static final Logger LOG = LoggerFactory.getLogger(DailyReportScheduler.class);
    private static final Duration LOCK_AT_MOST = Duration.ofMinutes(30);
    private static final Duration LOCK_AT_LEAST = Duration.ofMinutes(1);

    private final ReportRunService runService;
    private final ProfileRegistry profiles;
    private final LockingTaskExecutor locks;
    private final ZoneId zone;

    /**
     * Creates the scheduler.
     *
     * @param runService run service
     * @param profiles state profiles
     * @param locks ShedLock executor
     * @param properties reporting configuration (business zone)
     */
    public DailyReportScheduler(ReportRunService runService, ProfileRegistry profiles, LockingTaskExecutor locks,
            ReportingProperties properties) {
        this.runService = runService;
        this.profiles = profiles;
        this.locks = locks;
        this.zone = properties.getBusinessZone();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        for (String state : profiles.stateCodes()) {
            StateProfile profile = profiles.profile(state).orElseThrow();
            if (!profile.isEnabled()) {
                continue;
            }
            registrar.addCronTask(new CronTask(() -> runLocked(state), new CronTrigger(profile.getCron(), zone)));
            LOG.info("Daily report for state {} scheduled at '{}' {}", state, profile.getCron(), zone);
        }
    }

    /**
     * Starts the scheduled run of one state under its ShedLock lock.
     *
     * @param state state code
     */
    public void runLocked(String state) {
        locks.executeWithLock((Runnable) () -> startRun(state), new LockConfiguration(ClockProvider.now(),
                lockName(state), LOCK_AT_MOST, LOCK_AT_LEAST));
    }

    /**
     * Returns the ShedLock name of a state's daily job.
     *
     * @param state state code
     * @return {@code daily-report-{STATE}}
     */
    public static String lockName(String state) {
        return "daily-report-" + state;
    }

    void startRun(String state) {
        try {
            long runId = runService.startRun(StartRunCommand.builder().stateCode(state)
                    .triggerType(TriggerType.SCHEDULED).requestedBy(REQUESTER).build()).getRunId();
            LOG.info("Scheduled run {} started for state {}", runId, state);
        } catch (RunAlreadyActiveException e) {
            LOG.warn("Scheduled run for state {} skipped: {}", state, e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Scheduled run for state {} failed to start", state, e);
        }
    }
}
