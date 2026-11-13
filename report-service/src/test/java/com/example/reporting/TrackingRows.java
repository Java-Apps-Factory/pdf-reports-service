package com.example.reporting;

import static org.awaitility.Awaitility.await;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/** Test helper: inserts tracking rows directly and waits for run and lock states, using Awaitility. */
public final class TrackingRows {

    /** Upper bound for asynchronous work in integration tests; generous so a busy build machine cannot fail them. */
    public static final Duration TIMEOUT = Duration.ofMinutes(3);

    private final JdbcTemplate jdbc;

    /**
     * Creates the helper.
     *
     * @param jdbc JdbcTemplate of the database under test
     */
    public TrackingRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a run.
     *
     * @param status run status
     * @param now timestamp for data-as-of and creation
     * @return run ID
     */
    public long insertRun(String status, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO REPORT_RUN (STATE_CODE, TRIGGER_TYPE, "
                    + "REQUESTED_BY, STATUS, DATA_AS_OF, CREATED_AT) VALUES ('VA', 'API', 'test', ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, status);
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setTimestamp(3, Timestamp.from(now));
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    /**
     * Inserts a document.
     *
     * @param runId run ID
     * @param fips FIPS code
     * @param status document status
     * @param attempts generation attempts
     * @param leaseOwner lease owner or {@code null}
     * @param leaseExpiresAt lease expiry or {@code null}
     * @param now creation time
     * @return document ID
     */
    public long insertDocument(long runId, String fips, String status, int attempts, String leaseOwner,
            Instant leaseExpiresAt, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO REPORT_DOCUMENT (RUN_ID, STATE_CODE, "
                    + "FIPS_CODE, STATUS, GENERATION_ATTEMPTS, LEASE_OWNER, LEASE_EXPIRES_AT, CREATED_AT, UPDATED_AT) "
                    + "VALUES (?, 'VA', ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, runId);
            ps.setString(2, fips);
            ps.setString(3, status);
            ps.setInt(4, attempts);
            ps.setString(5, leaseOwner);
            ps.setTimestamp(6, leaseExpiresAt == null ? null : Timestamp.from(leaseExpiresAt));
            ps.setTimestamp(7, Timestamp.from(now));
            ps.setTimestamp(8, Timestamp.from(now));
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    /**
     * Stores content for a document and sets its PDF facts as generation would (SHA-256, size, generated time).
     *
     * @param docId document ID
     * @param pdf bytes to store
     * @param sha256 hex SHA-256 to record
     * @param generatedAt generation time
     */
    public void storeContent(long docId, byte[] pdf, String sha256, Instant generatedAt) {
        jdbc.update("INSERT INTO REPORT_CONTENT (DOC_ID, PDF) VALUES (?, ?)", ps -> {
            ps.setLong(1, docId);
            ps.setBytes(2, pdf);
        });
        jdbc.update("UPDATE REPORT_DOCUMENT SET SHA256 = ?, SIZE_BYTES = ?, RECORD_COUNT = 1, PAGE_COUNT = 1, "
                + "GENERATED_AT = ? WHERE DOC_ID = ?", sha256, pdf.length, Timestamp.from(generatedAt), docId);
    }

    /**
     * Reads one document row.
     *
     * @param docId document ID
     * @return column name to value
     */
    public Map<String, Object> document(long docId) {
        return jdbc.queryForMap("SELECT * FROM REPORT_DOCUMENT WHERE DOC_ID = ?", docId);
    }

    /**
     * Returns a run's status.
     *
     * @param runId run ID
     * @return status
     */
    public String runStatus(long runId) {
        return jdbc.queryForObject("SELECT STATUS FROM REPORT_RUN WHERE RUN_ID = ?", String.class, runId);
    }

    /**
     * Returns the statuses of a run's documents.
     *
     * @param runId run ID
     * @return statuses by FIPS code
     */
    public List<Map<String, Object>> documentsOf(long runId) {
        return jdbc.queryForList("SELECT DOC_ID, FIPS_CODE, STATUS, GENERATION_ATTEMPTS, RECORD_COUNT, LAST_ERROR "
                + "FROM REPORT_DOCUMENT WHERE RUN_ID = ? ORDER BY FIPS_CODE", runId);
    }

    /**
     * Waits until no run of VA holds the state lock.
     */
    public void awaitVirginiaLockFree() {
        await().atMost(TIMEOUT).until(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM RPT_STATE WHERE STATE_CODE = 'VA' AND ACTIVE_RUN_ID IS NOT NULL",
                Integer.class) == 0);
    }

    /**
     * Waits until a run reaches one of the statuses.
     *
     * @param runId run ID
     * @param statuses accepted statuses
     */
    public void awaitRunStatus(long runId, String... statuses) {
        await().atMost(TIMEOUT).until(() -> Arrays.asList(statuses).contains(runStatus(runId)));
    }
}
