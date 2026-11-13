package com.example.reporting.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.reporting.TrackingRows;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/** The scheduled path with the real ShedLock provider on H2 (database time). */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles({"h2", "test"})
@TestConstructor(autowireMode = AutowireMode.ALL)
class DailyReportSchedulerIT {

    private final DailyReportScheduler scheduler;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final JdbcTemplate jdbc;
    private final TrackingRows rows;

    DailyReportSchedulerIT(DailyReportScheduler scheduler, ThreadPoolTaskScheduler taskScheduler,
            JdbcTemplate jdbc) {
        this.scheduler = scheduler;
        this.taskScheduler = taskScheduler;
        this.jdbc = jdbc;
        this.rows = new TrackingRows(jdbc);
    }

    @Test
    void aScheduledRunStartsOnceWhileItsLockIsHeld() {
        rows.awaitVirginiaLockFree();
        int before = scheduledRuns().size();

        scheduler.runLocked("VA");
        scheduler.runLocked("VA");

        List<Long> runs = scheduledRuns();
        assertThat(runs).hasSize(before + 1);
        assertThat(jdbc.queryForObject("SELECT REQUESTED_BY FROM REPORT_RUN WHERE RUN_ID = ?", String.class,
                runs.get(0))).isEqualTo(DailyReportScheduler.REQUESTER);
        assertThat(jdbc.queryForList("SELECT NAME FROM SHEDLOCK", String.class)).contains("daily-report-VA");
        rows.awaitRunStatus(runs.get(0), "DISPATCHING", "COMPLETED");
    }

    @Test
    void backgroundJobsTakeTheirShedLocks() {
        await().atMost(TrackingRows.TIMEOUT).untilAsserted(() ->
                assertThat(jdbc.queryForList("SELECT NAME FROM SHEDLOCK", String.class))
                        .contains("run-finalizer", "lease-recovery"));
    }

    @Test
    void jobsRunOnTheirOwnSixThreadScheduler() {
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(6);
        assertThat(taskScheduler.getThreadNamePrefix()).isEqualTo("scheduling-");
    }

    private List<Long> scheduledRuns() {
        return jdbc.queryForList("SELECT RUN_ID FROM REPORT_RUN WHERE TRIGGER_TYPE = 'SCHEDULED' "
                + "ORDER BY RUN_ID DESC", Long.class);
    }
}
