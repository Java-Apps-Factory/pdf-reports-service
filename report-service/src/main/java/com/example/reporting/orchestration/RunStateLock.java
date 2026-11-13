package com.example.reporting.orchestration;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The per-state run lock: {@code RPT_STATE.ACTIVE_RUN_ID} is non-null while a run is in its generation phase
 * (FR-11). Acquire and release are conditional UPDATEs, so they work across nodes and join the caller's
 * transaction.
 */
@Component
public class RunStateLock {

    private static final String ACQUIRE =
            "UPDATE RPT_STATE SET ACTIVE_RUN_ID = ? WHERE STATE_CODE = ? AND ACTIVE_RUN_ID IS NULL";
    private static final String RELEASE =
            "UPDATE RPT_STATE SET ACTIVE_RUN_ID = NULL WHERE STATE_CODE = ? AND ACTIVE_RUN_ID = ?";
    private static final String ACTIVE = "SELECT ACTIVE_RUN_ID FROM RPT_STATE WHERE STATE_CODE = ?";

    private final JdbcTemplate jdbc;

    /**
     * Creates the lock.
     *
     * @param jdbc application JdbcTemplate
     */
    public RunStateLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Takes the lock for a run.
     *
     * @param stateCode state code
     * @param runId run taking the lock
     * @return true if taken; false if another run holds it
     */
    public boolean acquire(String stateCode, long runId) {
        return jdbc.update(ACQUIRE, runId, stateCode) == 1;
    }

    /**
     * Releases the lock if the run holds it.
     *
     * @param stateCode state code
     * @param runId run holding the lock
     * @return true if released
     */
    public boolean release(String stateCode, long runId) {
        return jdbc.update(RELEASE, stateCode, runId) == 1;
    }

    /**
     * Returns the run holding the lock.
     *
     * @param stateCode state code
     * @return run ID, or empty if the lock is free
     */
    public Optional<Long> activeRun(String stateCode) {
        List<Long> ids = jdbc.query(ACTIVE, (rs, n) -> {
            long id = rs.getLong(1);
            return rs.wasNull() ? null : id;
        }, stateCode);
        return ids.stream().filter(Objects::nonNull).findFirst();
    }
}
