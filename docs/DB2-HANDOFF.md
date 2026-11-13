# DB2 hand-off notes for the DBA

The service runs unchanged on IBM DB2 LUW 11.5 with the Spring profiles `db2,sib,prod`. Flyway creates and
migrates the schema on startup from `db/migration/common` (and `db/migration/db2`, empty today).

## Before the first deployment

1. **Database user** with CREATE TABLE / INDEX rights in the target schema (Flyway runs as this user), or
   pre-create the tables from `report-service/src/main/resources/db/migration/common/V1–V4` and grant DML only.
2. **LOB tablespace for `REPORT_CONTENT`** (AD-07): a dedicated tablespace, no inline LOB length, no
   compression (PDFs are already compressed). Expected volume: about 133 PDFs per day for Virginia, up to
   ~10 MB each in the worst case, kept 90 days after sending, then purged nightly (`ContentPurgeJob`, 03:30).
   If the tablespace must be named in DDL, add `db/migration/db2/V5__report_content_tablespace.sql` rather than
   changing the common scripts.
3. **Source table index**: once the real source table replaces `RPT_RECORD`, confirm an index matching
   `IX_RPT_RECORD_REPORT (STATE_CODE, FIPS_CODE, CATEGORY_CODE, LAST_NAME, FIRST_NAME, CASE_NUMBER)`; the report
   query (`sql/report-rows.sql`) streams in that order with `FOR READ ONLY` and fetch size 500.
4. **Connections**: the pool opens up to 12 connections per node (`spring.datasource.hikari.maximum-pool-size`).
5. **Time zone**: the JVM time zone of every node (`-Duser.timezone` in `setenv.sh`) must equal the DB2 server's,
   because `CURRENT TIMESTAMP` is read through it.

## Environment for the `db2` profile

`DB2_HOST`, `DB2_PORT`, `DB2_DATABASE`, `DB2_USER`, `DB2_PASSWORD` (see `application-db2.yml`). The clock query
is `SELECT CURRENT TIMESTAMP FROM SYSIBM.SYSDUMMY1`.

## Tables and growth

| Table | Rows | Notes |
|---|---|---|
| `RPT_STATE`, `RPT_FIPS`, `RPT_CATEGORY` | tens / hundreds | Reference data; Virginia seeded by `R__seed_va_reference.sql`. |
| `REPORT_RUN` | ~1 per state per day | Never purged. |
| `REPORT_DOCUMENT` | ~133 per VA run | Never purged; indexes `IX_DOC_WORK`, `IX_DOC_LEASE`, `IX_DOC_LATEST`, `IX_DOC_RUN`. |
| `REPORT_CONTENT` | one BLOB per generated document | Purged 90 days after SENT. |
| `SHEDLOCK` | one row per job lock | Tiny. |

## Compatibility check

`mvn -Pdb2-it verify` runs `Db2CompatibilityIT` against IBM's DB2 community image with Testcontainers: Flyway
migrations, the claim protocol and a full run on DB2. It needs Docker and pulls a large image (several GB);
run it nightly or before each release.
