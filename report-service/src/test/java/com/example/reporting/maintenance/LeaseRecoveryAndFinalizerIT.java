package com.example.reporting.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reporting.TrackingRows;
import com.example.reporting.orchestration.RunFinalizer;
import com.example.reporting.tracking.DbClock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * Expired leases are recovered (SPEC section 9, Phase 3 acceptance) and the finalizer completes runs whose
 * documents are all final.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles({"h2", "test"})
@TestConstructor(autowireMode = AutowireMode.ALL)
class LeaseRecoveryAndFinalizerIT {

    private final LeaseRecoverySweeper sweeper;
    private final RunFinalizer finalizer;
    private final DbClock clock;
    private final TrackingRows rows;
    private final JdbcTemplate jdbc;

    LeaseRecoveryAndFinalizerIT(LeaseRecoverySweeper sweeper, RunFinalizer finalizer, DbClock clock,
            JdbcTemplate jdbc) {
        this.sweeper = sweeper;
        this.finalizer = finalizer;
        this.clock = clock;
        this.jdbc = jdbc;
        this.rows = new TrackingRows(jdbc);
    }

    @Test
    void expiredLeasesAreRecoveredAndLiveLeasesAreLeftAlone() {
        Instant now = clock.now();
        Instant expired = now.minus(Duration.ofMinutes(1));
        long runId = rows.insertRun("DISPATCHING", now);
        long retry = rows.insertDocument(runId, "51059", "GENERATING", 1, "dead-node", expired, now);
        long lastAttempt = rows.insertDocument(runId, "51013", "GENERATING", 3, "dead-node", expired, now);
        long sending = rows.insertDocument(runId, "51107", "SENDING", 1, "dead-node", expired, now);
        long alive = rows.insertDocument(runId, "51153", "GENERATING", 1, "live-node",
                now.plus(Duration.ofMinutes(10)), now);

        sweeper.sweep();

        Map<String, Object> retried = rows.document(retry);
        assertThat(retried.get("STATUS")).asString().isEqualTo("GENERATION_FAILED");
        assertThat(retried.get("LEASE_OWNER")).isNull();
        assertThat(retried.get("LEASE_EXPIRES_AT")).isNull();
        assertThat(retried.get("LAST_ERROR")).asString().contains("dead-node", "expired");
        assertThat(((Timestamp) retried.get("NEXT_ATTEMPT_AT")).toInstant())
                .isAfter(now.plus(Duration.ofSeconds(50)));
        assertThat(rows.document(lastAttempt).get("STATUS")).asString().isEqualTo("GENERATION_FAILED_FINAL");
        Map<String, Object> resend = rows.document(sending);
        assertThat(resend.get("STATUS")).asString().isEqualTo("SEND_FAILED");
        assertThat(((Timestamp) resend.get("NEXT_ATTEMPT_AT")).toInstant()).isBeforeOrEqualTo(clock.now());
        Map<String, Object> live = rows.document(alive);
        assertThat(live.get("STATUS")).asString().isEqualTo("GENERATING");
        assertThat(live.get("LEASE_OWNER")).isEqualTo("live-node");

        assertThat(sweeper.sweep()).as("a second sweep finds nothing new").isZero();
        // Leave no live GENERATING row behind for other tests.
        jdbc.update("UPDATE REPORT_DOCUMENT SET STATUS = 'GENERATION_FAILED_FINAL', LEASE_OWNER = NULL, "
                + "LEASE_EXPIRES_AT = NULL WHERE DOC_ID = ?", alive);
    }

    @Test
    void dispatchingRunsCompleteWhenEveryDocumentIsFinal() {
        Instant now = clock.now();
        long clean = rows.insertRun("DISPATCHING", now);
        rows.insertDocument(clean, "51059", "SENT", 1, null, null, now);
        rows.insertDocument(clean, "51013", "SUPERSEDED", 1, null, null, now);
        long failed = rows.insertRun("DISPATCHING", now);
        rows.insertDocument(failed, "51059", "SENT", 1, null, null, now);
        rows.insertDocument(failed, "51013", "SEND_FAILED_FINAL", 1, null, null, now);
        long open = rows.insertRun("DISPATCHING", now);
        rows.insertDocument(open, "51059", "SENT", 1, null, null, now);
        rows.insertDocument(open, "51013", "GENERATED", 1, null, null, now);

        finalizer.finalizeRuns();
        rows.awaitRunStatus(clean, "COMPLETED");
        rows.awaitRunStatus(failed, "COMPLETED_WITH_ERRORS");

        assertThat(rows.runStatus(open)).isEqualTo("DISPATCHING");
        assertThat(jdbc.queryForObject("SELECT COMPLETED_AT FROM REPORT_RUN WHERE RUN_ID = ?", Timestamp.class,
                clean)).isNotNull();
        // Leave the open run finished for other tests.
        jdbc.update("UPDATE REPORT_DOCUMENT SET STATUS = 'SENT' WHERE RUN_ID = ?", open);
    }
}
