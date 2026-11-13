# Runbook — FIPS Report Service

Operators answer "did today's reports go out, and if not, which ones and why" from the API and metrics; logs are
only needed for stack traces. All API calls are under `/api/v1`; add `-H 'X-Requested-By: <your id>'` to POSTs.

## 1. Check today's run

```bash
curl -s 'http://<node>:8080/api/v1/report-runs?stateCode=VA&size=5'
curl -s http://<node>:8080/api/v1/report-runs/<runId>
```

- `status`: RUNNING (generating) → DISPATCHING (sending) → COMPLETED or COMPLETED_WITH_ERRORS.
- `documentsByStatus` shows where every document is. Healthy end state: only `SENT` (and `SUPERSEDED`).
- Failed documents: `GET /report-runs/<runId>/documents?status=SEND_FAILED_FINAL` (or `GENERATION_FAILED_FINAL`,
  `DATA_ERROR`); each has `lastError`, attempts and `nextAttemptAt`.
- The daily run starts at 02:00 America/New_York. Alert if it has not reached DISPATCHING by 04:00.
- Metrics (`/actuator/prometheus` in `prod`): `reporting_documents_by_status`, `reporting_send_failures_total`,
  `reporting_leases_recovered_total`, `reporting_runs_started_total`.

## 2. Retransmit

The stored PDF is resent as-is; nothing is regenerated.

```bash
# one document (SEND_FAILED or SEND_FAILED_FINAL)
curl -s -X POST http://<node>:8080/api/v1/report-documents/<docId>/retransmit -H 'X-Requested-By: jdoe'
# every failed document of a run
curl -s -X POST http://<node>:8080/api/v1/report-runs/<runId>/retransmit-failed -H 'X-Requested-By: jdoe'
```

- 409: the document is SUPERSEDED (a newer report for that FIPS code exists) or not in a failed status.
- 410: its PDF was purged (90 days after sending). Start a new run for that FIPS code instead (section 6).
- The run returns to DISPATCHING and completes again once the resend succeeds.
- Downstream de-duplicates on the `reportDocId` message property, so a resend is safe.

## 3. DATA_ERROR

The source data cannot be reported as-is; retrying cannot fix it. `lastError` says which:

- `FIPS code not in RPT_FIPS for state` — records exist for a FIPS code that is missing or inactive in `RPT_FIPS`.
  The **reference-data owner** adds or activates the code (a data change, then a new run).
- `Record with category CATX that is not an active category of state VA` — the **source-data owner** fixes the
  records' category, or the reference-data owner adds/activates the category (and its YAML profile if it needs
  its own columns).

After the fix, start a run limited to the affected FIPS codes (section 6).

## 4. A run stuck in RUNNING

A run stays RUNNING while any document is PENDING, GENERATING or GENERATION_FAILED.

```sql
SELECT STATUS, COUNT(*) FROM REPORT_DOCUMENT WHERE RUN_ID = ? GROUP BY STATUS;
SELECT DOC_ID, FIPS_CODE, LEASE_OWNER, LEASE_EXPIRES_AT, GENERATION_ATTEMPTS, NEXT_ATTEMPT_AT, LAST_ERROR
  FROM REPORT_DOCUMENT WHERE RUN_ID = ? AND STATUS IN ('PENDING', 'GENERATING', 'GENERATION_FAILED');
SELECT STATE_CODE, ACTIVE_RUN_ID FROM RPT_STATE;
```

- GENERATING with `LEASE_EXPIRES_AT` in the past: the node died; `LeaseRecoverySweeper` (every 60 s) moves it
  back to GENERATION_FAILED. If it does not, check that some node is up and `/actuator/health` is UP.
- GENERATION_FAILED with `NEXT_ATTEMPT_AT` in the future: waiting for its retry (1, then 2 minutes).
- A generation that takes longer than 80% of the 15-minute lease logs a WARN; raise
  `reporting.generation.lease-duration` if that happens.
- `RunFinalizer` moves the run on within 30 s once nothing is open; it releases `RPT_STATE.ACTIVE_RUN_ID`.

## 5. Clear a stale state lock (only if section 4 does not resolve it)

A new run for a state is refused (HTTP 409) while `RPT_STATE.ACTIVE_RUN_ID` is set. Clear it only when the run
it points to is no longer generating:

```sql
-- 1. the run must have no open generation work
SELECT COUNT(*) FROM REPORT_DOCUMENT WHERE RUN_ID = ? AND STATUS IN ('PENDING', 'GENERATING', 'GENERATION_FAILED');
-- 2. if 0: release the lock and let the finalizer complete the run
UPDATE RPT_STATE SET ACTIVE_RUN_ID = NULL WHERE STATE_CODE = 'VA' AND ACTIVE_RUN_ID = ?;
UPDATE REPORT_RUN SET STATUS = 'DISPATCHING' WHERE RUN_ID = ? AND STATUS = 'RUNNING';
```

Never clear the lock while documents are still GENERATING with a live lease: two runs would then generate the same
FIPS codes at once (the older one is superseded at send time, but the work is wasted).

## 6. Start an ad-hoc run

```bash
curl -s -X POST http://<node>:8080/api/v1/report-runs -H 'Content-Type: application/json' \
  -H 'X-Requested-By: jdoe' -d '{"stateCode":"VA","fipsCodes":["51059"]}'
```

`fipsCodes` is optional (all FIPS codes with records). Allowed any time no other run of the state is generating.

## 7. Download a PDF

```bash
curl -s -OJ http://<node>:8080/api/v1/report-documents/<docId>/content
```

404 when no PDF is stored (e.g. DATA_ERROR), 410 when purged.

## 8. Switch from H2 to DB2

1. Hand `docs/DB2-HANDOFF.md` to the DBA (user, LOB tablespace, time zone).
2. Set `DB2_HOST`, `DB2_PORT`, `DB2_DATABASE`, `DB2_USER`, `DB2_PASSWORD` for Tomcat.
3. Activate profiles `db2,sib,prod` (already in `deploy/tomcat/bin/setenv.sh`); do **not** activate `h2`.
4. Start one node first: Flyway migrates the empty schema and seeds the Virginia reference data. Then start the
   others. `mvn -Pdb2-it verify` checks the SQL against DB2 before a release.

## 9. Rotate SIB credentials

1. The SIB administrator creates the new password (or user).
2. Update `SIB_USER` / `SIB_PASSWORD` in each node's Tomcat environment.
3. Restart nodes one at a time. Sends in flight on a stopped node are recovered by lease expiry (5 minutes) and
   resent; downstream de-duplicates on `reportDocId`.
4. Check `/actuator/health` (JMS UP) and that the next documents reach SENT.

## 10. Alerts (configure in the monitoring tool)

- Any increase of documents in SEND_FAILED_FINAL, GENERATION_FAILED_FINAL or DATA_ERROR
  (`reporting_documents_by_status`).
- The scheduled run of a state has not reached DISPATCHING by 04:00 America/New_York.
- `reporting_leases_recovered_total` increases (a node died or a lease is too short).
- JMS health DOWN for more than 5 minutes (`/actuator/health`).

## 11. Local development

- Build: `mvn verify` with JDK 11 (`export JAVA_HOME=$(/usr/libexec/java_home -v 11)`).
- Run: `java -jar report-service/target/report-service.war --spring.profiles.active=h2` (H2 file in `./data`,
  embedded Artemis, 5,000 fake records). Delete `./data` to start clean.
- Performance check: `mvn -Pperf verify -pl report-service -am` (section 21, NFR-03).
