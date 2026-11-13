package com.example.reporting.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.reporting.orchestration.ReportRunService;
import com.example.reporting.orchestration.RunAlreadyActiveException;
import com.example.reporting.orchestration.StartRunCommand;
import com.example.reporting.profile.ProfileRegistry;
import com.example.reporting.profile.ProfileValidator;
import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.profile.StateProfile;
import com.example.reporting.profile.TestProfiles;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.TriggerType;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class DailyReportSchedulerTest {

    private final ReportRunService runService = mock(ReportRunService.class);
    private final LockingTaskExecutor locks = mock(LockingTaskExecutor.class);

    @Test
    void registersOneCronTaskPerEnabledState() {
        StateProfile va = TestProfiles.virginia();
        StateProfile disabled = new StateProfile(false, "0 0 3 * * *", "T", "", "", "m", va.getLogo(),
                va.getColumns(), Map.of());
        DailyReportScheduler scheduler = scheduler(Map.of("VA", va, "MD", disabled));
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        scheduler.configureTasks(registrar);

        assertThat(registrar.getCronTaskList()).singleElement()
                .extracting(CronTask::getExpression).isEqualTo("0 0 2 * * *");
    }

    @Test
    void startsAScheduledRunUnderTheStatesLock() {
        doAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return null;
        }).when(locks).executeWithLock(any(Runnable.class), any(LockConfiguration.class));
        ReportRun run = ReportRun.start("VA", TriggerType.SCHEDULED, "scheduler", null, Instant.now());
        when(runService.startRun(any())).thenReturn(run);

        scheduler(Map.of("VA", TestProfiles.virginia())).runLocked("VA");

        ArgumentCaptor<LockConfiguration> lock = ArgumentCaptor.forClass(LockConfiguration.class);
        verify(locks).executeWithLock(any(Runnable.class), lock.capture());
        assertThat(lock.getValue().getName()).isEqualTo("daily-report-VA");
        assertThat(lock.getValue().getLockAtMostFor()).isEqualTo(Duration.ofMinutes(30));
        assertThat(lock.getValue().getLockAtLeastFor()).isEqualTo(Duration.ofMinutes(1));
        ArgumentCaptor<StartRunCommand> command = ArgumentCaptor.forClass(StartRunCommand.class);
        verify(runService).startRun(command.capture());
        assertThat(command.getValue().triggerType()).isEqualTo(TriggerType.SCHEDULED);
        assertThat(command.getValue().requestedBy()).isEqualTo(DailyReportScheduler.REQUESTER);
        assertThat(command.getValue().fipsCodes()).isEmpty();
    }

    @Test
    void anActiveRunOrAFailureIsLoggedNotRaised() {
        DailyReportScheduler scheduler = scheduler(Map.of("VA", TestProfiles.virginia()));
        doThrow(new RunAlreadyActiveException("VA", Optional.of(1L))).when(runService).startRun(any());
        scheduler.startRun("VA");
        doThrow(new IllegalStateException("database down")).when(runService).startRun(any());
        scheduler.startRun("VA");
        verify(runService, times(2)).startRun(any());
    }

    private DailyReportScheduler scheduler(Map<String, StateProfile> states) {
        ReportingProperties properties = TestProfiles.properties(states, Duration.ofMinutes(15), Paths.get("tmp"));
        ProfileRegistry registry = new ProfileRegistry(properties, mock(ProfileValidator.class),
                new DefaultResourceLoader());
        return new DailyReportScheduler(runService, registry, locks, properties);
    }
}
