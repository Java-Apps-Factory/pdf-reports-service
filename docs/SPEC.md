# FIPS Report Service — Requirements & Design Specification

Nov 10, 2026 · @Vadivel Murugesan

## 1. Purpose and how to use this spec

This spec defines a Spring Boot service that generates one accessible PDF per state FIPS code from cumulative data and sends each PDF to a downstream system over WebSphere SIB. It is the single source of truth for Claude Code: every decision here was reviewed and agreed, so code should follow it rather than re-decide it.

**How to use it with Claude Code**

- Download this doc as Markdown and save it in the repository as `docs/SPEC.md`.
- Create `CLAUDE.md` in the repository root with the content below, so every Claude Code session loads the rules.
- Build one phase per session, in the order of section 21. Review, run the tests, and commit before starting the next phase.
- Start each session with: `Implement Phase N from docs/SPEC.md. Follow CLAUDE.md. Stop when the phase acceptance criteria pass and summarize what you built.`

```
# CLAUDE.md

This project implements docs/SPEC.md. Read the sections relevant to the current phase before writing code.

## Non-negotiable rules
- Java 11 language level, Spring Boot 2.7.18, javax.* APIs. Do not use Java 17+ features or Jakarta packages.
- Follow the decisions in SPEC.md section 4. Do not add Spring Batch, Lombok, XA transactions, or new frameworks.
- Base package: com.example.reporting. report-pdf-core must not depend on Spring.
- SQL must run unchanged on H2 (MODE=DB2) and IBM DB2 LUW. Put vendor-specific SQL only in db/migration/{vendor} or in config.
- Never load all report rows into memory. Stream rows straight into the PDF writer.
- Never log record contents (names, case numbers). Log only ids, counts, FIPS codes, and statuses.
- Constructor injection only. Immutable value classes with builders. No field injection.
- Every public class in report-pdf-core has Javadoc. Every behavior in SPEC.md acceptance criteria has a test.

## When the spec is silent
Choose the simplest option consistent with SPEC.md and record it in docs/DECISIONS.md (date, decision, reason).

## Build and test
- mvn -q verify runs unit tests, integration tests, Checkstyle, SpotBugs, and JaCoCo.
- Local run: mvn -pl report-service spring-boot:run -Dspring-boot.run.profiles=h2
```

## 2. Functional requirements

The service produces one cumulative PDF per state and FIPS code, every day and on demand, and delivers each PDF individually with tracked, retried transmission.

| ID | Requirement |
|---|---|
| FR-01 | Generate one PDF per (state, FIPS code) containing all records currently in the source table for that state and FIPS code. Reports are cumulative; there is no date filter. |
| FR-02 | Skip FIPS codes with zero records: no PDF and no tracking row. |
| FR-03 | Group records into category sections, ordered by the category display order configured for the state. |
| FR-04 | Show every active category of the state, even with no records: section heading, table header row, then the message "No content to display in this category." |
| FR-05 | Page 1 shows a title block (logo, title, state, FIPS code and locality, data-as-of time, report ID), then a summary table with the record count per category (including zeros) and a total. |
| FR-06 | Every page has a header and footer. Page 1 uses the title block as its header; pages 2+ use a compact running header. The table header row repeats on every continuation page. |
| FR-07 | Adding a state, FIPS code, or category that uses the standard template needs only reference data rows and a YAML profile entry, never code changes. |
| FR-08 | Per state, these are configurable: title, subtitle, footer disclaimer, logo and its alt text, empty-category message, table columns, per-category column overrides, and the daily schedule. |
| FR-09 | Run a daily scheduled job per enabled state. Default: 02:00 America/New_York. |
| FR-10 | Provide a REST API to start a run for a state, optionally limited to a list of FIPS codes. |
| FR-11 | Allow only one run per state in the generation phase at a time, across all nodes. A second request returns HTTP 409. Ad-hoc runs may happen on the same day as the scheduled run. |
| FR-12 | Send each PDF as its own JMS `BytesMessage` to WebSphere SIB, with the metadata properties in section 13. |
| FR-13 | Track each run and each document's status, attempts, errors, and timestamps in the database. |
| FR-14 | Retry failed sends automatically using the stored PDF. A resend never regenerates the PDF. |
| FR-15 | Never send a document when a newer document for the same state and FIPS code exists in status GENERATED, SENDING, or SENT. Mark the older one SUPERSEDED instead. |
| FR-16 | Provide manual retransmission for one document and for all failed documents of a run. |
| FR-17 | Provide download of a stored PDF through the API. |
| FR-18 | Purge stored PDF content 90 days after SENT (configurable). Tracking metadata is kept. |
| FR-19 | Print a "Data as of" timestamp on each report: the run start time in the business time zone. |
| FR-20 | Never drop data silently. A FIPS code not in the reference table, or a record with an unknown category, puts the document in DATA_ERROR with a clear message. |
| FR-21 | Show the agency logo in the title block. A neutral placeholder image ships with the code until the real logo is provided. |

## 3. Non-functional requirements

The service must stay memory-flat up to 100,000 records per run, survive node crashes without losing work, and switch from H2 to DB2 by configuration alone.

| ID | Area | Requirement |
|---|---|---|
| NFR-01 | Volume | Up to 100,000 records per run in total. Any single FIPS code may hold all of them (about 2,500 pages). |
| NFR-02 | Memory | Generation never holds all rows or the whole PDF in heap. Only a send holds one PDF as a byte array, and sends are capped at 2 concurrent per node. |
| NFR-03 | Performance | Proposed target, to be confirmed by the Phase 6 performance test: a 100,000-record run finishes within 10 minutes on one node with `-Xmx2g`. |
| NFR-04 | Resilience | If a node crashes, its claimed work is picked up by another node after the lease expires (generation 15 min, send 5 min by default). No work is lost on restart. |
| NFR-05 | Scalability | Runs on multiple Tomcat nodes. Work spreads across nodes automatically through database claims. |
| NFR-06 | Portability | H2 2.x in DB2 mode for development; IBM DB2 LUW for production. The switch is a Spring profile, a JDBC driver, and the Flyway vendor folder. |
| NFR-07 | Accessibility | PDFs conform to PDF/UA-1 and PDF/A-2a, checked by veraPDF in the build (section 12). |
| NFR-08 | Delivery | At-least-once delivery to SIB. Duplicates are possible after a crash and are removed downstream using `reportDocId`. |
| NFR-09 | Observability | Health checks, metrics, and structured logs with run and document context (section 18). |
| NFR-10 | Maintainability | Unit-test line coverage of at least 80% in report-pdf-core and in the service's orchestration, tracking, and transmission packages. Checkstyle and SpotBugs pass with no violations. |
| NFR-11 | Compatibility | Java 11, Spring Boot 2.7.18, external Tomcat 9.0.x, WebSphere Application Server 9.0 SIB. |
| NFR-12 | Security | Out of scope for now (section 22). The code must leave a single place to add Spring Security later, and the `prod` profile logs a warning at startup while security is absent. |
| NFR-13 | Privacy | Records may contain names. They appear only inside PDFs, never in logs, metrics, JMS properties, or error messages. |

## 4. Architecture overview and key decisions

One Spring Boot WAR runs on every Tomcat node. The database is the work queue: any node can claim and process any document, and a separate Spring-free module renders the PDFs.

The pipeline is: trigger (scheduler or API) → create run and one PENDING document per FIPS code → any node claims and generates → any node claims and sends → background jobs recover leases, finalize runs, and purge content.

*(architecture diagram — see the original document)*

service architecture · 2 triggers, shared database, 2 worker pools

Workers on every node claim documents from the database in both directions of the double arrows; the dashed arrow is the after-commit wake-up from generation to sending.

**Architecture decisions.** These are fixed; do not change them without approval.

| ID | Decision | Reason |
|---|---|---|
| AD-01 | Two Maven modules: `report-pdf-core` (no Spring) and `report-service` (Spring Boot WAR). | The PDF engine is a reusable framework with a small, stable public API. |
| AD-02 | The database is the source of truth for work. Documents are claimed with a lease through a conditional UPDATE. Every node polls for work. | Restart-safe and multi-node-safe. No in-memory queue can lose work. |
| AD-03 | Lease times use the database clock, never node clocks. | Clock drift between nodes cannot cause double processing. |
| AD-04 | Report rows are read with `JdbcTemplate` streaming (`FOR READ ONLY`, fetch size 500). Tracking tables use Spring Data JPA. | Streaming keeps heap flat and enables DB2 block fetch. JPA suits small CRUD with optimistic locking. |
| AD-05 | Summary counts are computed from the same row stream that renders the tables, then drawn into space reserved on page 1. | The report is always internally consistent, even while new rows are inserted, without any snapshot column. |
| AD-06 | Headers, footers, and page numbers are stamped in a second pass over the finished pages. | "Page X of Y" needs the total. Header and footer logic stays separate from table logic. |
| AD-07 | The PDF is stored as a BLOB in `REPORT_CONTENT`, separate from the tracking row. Resends use the stored bytes. | Retransmissions are identical and verifiable by SHA-256. Status queries never touch BLOBs. |
| AD-08 | No XA. The tracking table acts as a transactional outbox, and every message carries an idempotency key (`reportDocId`). | Tomcat has no JTA. At-least-once delivery plus downstream de-duplication is the honest guarantee. |
| AD-09 | Superseding rule (FR-15) is checked at send time. | A stale report can never be delivered after a newer one. |
| AD-10 | Reference data (states, FIPS codes, categories) lives in the database. Presentation (text, logo, columns) lives in YAML profiles. Startup validation checks they agree. | One clear home for each kind of setting. |
| AD-11 | No Spring Batch, no Lombok, no plugin loading, no rules engine. | A claim-based worker pool and our tracking tables cover the need with far less machinery. |
| AD-12 | Accessibility tagging sits behind a `TaggingContext` interface with a no-op implementation (Null Object). | Reports work untagged before the tagging phase lands; tagging never leaks into layout code. |

**Design patterns in use.** Template Method (`FipsReportComposer` fixes the document skeleton), Strategy (`ReportDataProvider`, `CellFormatter`, `ReportTransmitter`), Registry (`ProfileRegistry`), Builder (all value objects in report-pdf-core), Decorator (the page-stamping pass), Observer (after-commit events that wake the send poller), Null Object (`NoOpTaggingContext`).

## 5. Tech stack and dependencies

Use Spring Boot 2.7.18 dependency management for every library it manages; pin the others explicitly in the parent POM. Versions marked "latest 4.x" or similar mean the newest patch in that line that still supports Java 11, checked at build time.

| Concern | Artifact / product | Version | Notes |
|---|---|---|---|
| Language | Java | 11 | `maven.compiler.release=11` |
| Framework | `spring-boot-starter-parent` | 2.7.18 | Starters: web, jdbc, data-jpa, validation, actuator, aop, jms |
| Packaging | WAR on external Tomcat | Tomcat 9.0.x | `spring-boot-starter-tomcat` scope `provided`; app extends `SpringBootServletInitializer` |
| PDF | `org.apache.pdfbox:pdfbox` | 3.0.x | Uses the 3.0 API (`Loader`, temp-file stream cache) |
| Dev database | `com.h2database:h2` | Boot-managed (2.1.x) | URL with `MODE=DB2;DATABASE_TO_UPPER=TRUE` |
| Prod database driver | `com.ibm.db2:jcc` | 11.5.x | Maven Central |
| Migrations | `org.flywaydb:flyway-core` | Boot-managed (8.5.x) | Locations `db/migration/common` and `db/migration/{vendor}` |
| Pool | HikariCP | Boot-managed | Sizing in section 16 |
| Messaging API | `spring-jms`, `javax.jms:javax.jms-api` | Boot-managed / 2.0.1 | Use only JMS 1.1 calls; the SIB thin client is a JMS 1.1 provider |
| SIB client | IBM JMS thin client for WAS 9.0 (`com.ibm.ws.sib.client.thin.jms_9.0.0.jar`) | 9.0 | From the WAS install's `runtimes` folder; publish to the internal Maven repository. Phase 0 confirms whether extra IBM jars (such as the ORB thin client) are needed on a non-IBM JDK. |
| Local and test broker | `spring-boot-starter-artemis` + `artemis-jms-server` | Boot-managed | Embedded broker for the `h2` profile and integration tests |
| Distributed locks | `net.javacrumbs.shedlock:shedlock-spring`, `shedlock-provider-jdbc-template` | latest 4.x | 4.x supports Java 8+; do not use 5.x |
| Retry | `spring-retry` | Boot-managed | In-process send retries only |
| Metrics | Micrometer + Actuator | Boot-managed |  |
| Fonts | Noto Sans Regular, Bold, Italic (TTF) | — | SIL Open Font License; bundled in `report-service` resources |
| Color profile | sRGB ICC profile | — | Bundled for the PDF/A output intent |
| Tests | JUnit 5, AssertJ, Mockito, Awaitility | Boot-managed |  |
| PDF validation | `org.verapdf:validation-model` | latest supporting Java 11 | Test scope only |
| Quality | Checkstyle, SpotBugs, JaCoCo Maven plugins | latest supporting Java 11 | Bound to `verify` |

## 6. Maven modules and package structure

Three POMs share the groupId `com.example.reporting`: `report-parent` (packaging `pom`), `report-pdf-core` (jar), and `report-service` (war, depends on report-pdf-core). Class names below are binding; add private helpers freely.

```
report-parent/
├── pom.xml                                  # dependencyManagement, plugin config, quality gates
├── docs/SPEC.md, docs/DECISIONS.md, docs/RUNBOOK.md
├── report-pdf-core/src/main/java/com/example/reporting/pdf/
│   ├── ReportDocumentWriter.java            # public API (section 10)
│   ├── ReportDocumentWriters.java           # factory: open(DocumentSpec, Path workDir)
│   ├── DocumentSpec.java                    # title block, running header, footer, metadata, fonts, logo, summary rows
│   ├── SectionSpec.java, SummaryData.java, RenderResult.java
│   ├── layout/    PageGeometry, Margins, PageCursor, KeepTogetherPolicy
│   ├── table/     TableSpec, ColumnSpec, Alignment, CellValue, RowLayout, TableRenderer
│   ├── text/      FontSet, TextMeasurer, TextWrapper, GlyphSanitizer
│   ├── decorate/  PageDecorator, TitleBlockPainter, RunningHeaderPainter, FooterPainter, SummaryPainter
│   ├── outline/   BookmarkBuilder
│   └── a11y/      TaggingContext, NoOpTaggingContext, PdfUaTaggingContext, XmpMetadataWriter
└── report-service/src/main/java/com/example/reporting/
    ├── ReportServiceApplication.java        # extends SpringBootServletInitializer
    ├── api/            ReportRunController, ReportDocumentController, ApiExceptionHandler, ApiProblem, dto/*
    ├── scheduling/     DailyReportScheduler (SchedulingConfigurer, one CronTrigger per enabled state)
    ├── orchestration/  ReportRunService, RunStateLock, GenerationPoller, GenerationWorker, RunFinalizer
    ├── data/           ReportDataProvider, JdbcReportDataProvider, ReportRow, ReportRowMapper, FipsWorkload
    ├── profile/        ReportingProperties, StateProfile, ColumnDefinition, ProfileRegistry, ProfileValidator
    ├── rendering/      FipsReportComposer, CellFormatter, CellFormatters, SummaryCollector, DocumentSpecFactory
    ├── tracking/       ReportRun, ReportDocument, RunStatus, DocumentStatus, TriggerType,
    │                   ReportRunRepository, ReportDocumentRepository, DocumentClaimRepository (JDBC),
    │                   ReportContentStore (JDBC BLOB), DbClock, Backoff
    ├── transmission/   ReportTransmitter, JmsReportTransmitter, SendPoller, SendWorker,
    │                   ReportMessageProperties, SendFailureClassifier, DocumentGeneratedEvent
    ├── maintenance/    LeaseRecoverySweeper, ContentPurgeJob, TempFileJanitor
    └── config/         JmsConfig, SibConnectionFactoryConfig, ArtemisDevConfig, ExecutorConfig,
                        ShedLockConfig, ClockConfig, SecurityPlaceholderConfig

report-service/src/main/resources/
├── application.yml, application-h2.yml, application-db2.yml, application-sib.yml, application-prod.yml
├── db/migration/common/   V1__reference.sql, V3__tracking.sql, V4__shedlock.sql, R__seed_va_reference.sql
├── db/migration/h2/       V2__dev_source_records.sql
├── db/migration/db2/      (empty until DB2-specific DDL is needed, e.g. LOB tablespace)
├── sql/                   report-rows.sql, fips-workload.sql
├── fonts/                 NotoSans-Regular.ttf, NotoSans-Bold.ttf, NotoSans-Italic.ttf
├── color/                 sRGB.icc
└── branding/              placeholder-logo.png
```

**Dependency rules.** `api` and `scheduling` call only `orchestration` and `tracking`. `rendering` is the only package that imports `com.example.reporting.pdf`. `report-pdf-core` imports only PDFBox and the JDK.

## 7. Data model and schema

The schema has three groups: reference data that drives extensibility, an assumed source table that will be replaced by the real one, and tracking tables. All DDL below must run unchanged on H2 (MODE=DB2) and DB2 LUW.

```
-- V1__reference.sql (common)
CREATE TABLE RPT_STATE (
  STATE_CODE     CHAR(2)      NOT NULL PRIMARY KEY,
  STATE_NAME     VARCHAR(100) NOT NULL,
  ACTIVE         SMALLINT     DEFAULT 1 NOT NULL,
  ACTIVE_RUN_ID  BIGINT                         -- non-null while a run is in its generation phase (FR-11)
);
CREATE TABLE RPT_FIPS (
  FIPS_CODE      CHAR(5)      NOT NULL PRIMARY KEY,
  STATE_CODE     CHAR(2)      NOT NULL REFERENCES RPT_STATE(STATE_CODE),
  LOCALITY_NAME  VARCHAR(120) NOT NULL,
  ACTIVE         SMALLINT     DEFAULT 1 NOT NULL
);
CREATE TABLE RPT_CATEGORY (
  STATE_CODE     CHAR(2)      NOT NULL REFERENCES RPT_STATE(STATE_CODE),
  CATEGORY_CODE  VARCHAR(20)  NOT NULL,
  DISPLAY_NAME   VARCHAR(100) NOT NULL,
  DISPLAY_ORDER  INTEGER      NOT NULL,
  ACTIVE         SMALLINT     DEFAULT 1 NOT NULL,
  PRIMARY KEY (STATE_CODE, CATEGORY_CODE)
);

-- V2__dev_source_records.sql (h2 only; the real source table replaces this)
CREATE TABLE RPT_RECORD (
  RECORD_ID      BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  STATE_CODE     CHAR(2)       NOT NULL,
  FIPS_CODE      CHAR(5)       NOT NULL,
  CATEGORY_CODE  VARCHAR(20)   NOT NULL,
  CASE_NUMBER    VARCHAR(30)   NOT NULL,
  LAST_NAME      VARCHAR(60),
  FIRST_NAME     VARCHAR(60),
  EVENT_DATE     DATE,
  AMOUNT         DECIMAL(15,2),
  STATUS         VARCHAR(20),
  DESCRIPTION    VARCHAR(500)
);
CREATE INDEX IX_RPT_RECORD_REPORT ON RPT_RECORD
  (STATE_CODE, FIPS_CODE, CATEGORY_CODE, LAST_NAME, FIRST_NAME, CASE_NUMBER);

-- V3__tracking.sql (common)
CREATE TABLE REPORT_RUN (
  RUN_ID          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  STATE_CODE      CHAR(2)       NOT NULL,
  TRIGGER_TYPE    VARCHAR(10)   NOT NULL,      -- SCHEDULED | API
  REQUESTED_BY    VARCHAR(100)  NOT NULL,
  FIPS_FILTER     VARCHAR(2000),               -- comma-separated, null = all
  STATUS          VARCHAR(30)   NOT NULL,      -- RunStatus
  DATA_AS_OF      TIMESTAMP     NOT NULL,
  TOTAL_DOCUMENTS INTEGER       DEFAULT 0 NOT NULL,
  FAILURE_REASON  VARCHAR(1000),
  CREATED_AT      TIMESTAMP     NOT NULL,
  COMPLETED_AT    TIMESTAMP,
  VERSION         INTEGER       DEFAULT 0 NOT NULL
);
CREATE TABLE REPORT_DOCUMENT (
  DOC_ID              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  RUN_ID              BIGINT        NOT NULL REFERENCES REPORT_RUN(RUN_ID),
  STATE_CODE          CHAR(2)       NOT NULL,
  FIPS_CODE           CHAR(5)       NOT NULL,
  STATUS              VARCHAR(30)   NOT NULL,  -- DocumentStatus
  RECORD_COUNT        INTEGER,
  PAGE_COUNT          INTEGER,
  SIZE_BYTES          BIGINT,
  SHA256              CHAR(64),
  GENERATION_ATTEMPTS SMALLINT      DEFAULT 0 NOT NULL,
  SEND_ATTEMPTS       SMALLINT      DEFAULT 0 NOT NULL,
  NEXT_ATTEMPT_AT     TIMESTAMP,
  LEASE_OWNER         VARCHAR(100),
  LEASE_EXPIRES_AT    TIMESTAMP,
  LAST_ERROR          VARCHAR(2000),
  JMS_MESSAGE_ID      VARCHAR(100),
  GENERATED_AT        TIMESTAMP,
  SENT_AT             TIMESTAMP,
  CONTENT_PURGED_AT   TIMESTAMP,
  CREATED_AT          TIMESTAMP     NOT NULL,
  UPDATED_AT          TIMESTAMP     NOT NULL,
  VERSION             INTEGER       DEFAULT 0 NOT NULL
);
CREATE INDEX IX_DOC_WORK   ON REPORT_DOCUMENT (STATUS, NEXT_ATTEMPT_AT);
CREATE INDEX IX_DOC_LEASE  ON REPORT_DOCUMENT (STATUS, LEASE_EXPIRES_AT);
CREATE INDEX IX_DOC_LATEST ON REPORT_DOCUMENT (STATE_CODE, FIPS_CODE, GENERATED_AT);
CREATE INDEX IX_DOC_RUN    ON REPORT_DOCUMENT (RUN_ID, STATUS);
CREATE TABLE REPORT_CONTENT (
  DOC_ID  BIGINT     NOT NULL PRIMARY KEY REFERENCES REPORT_DOCUMENT(DOC_ID),
  PDF     BLOB(100M) NOT NULL
);

-- V4__shedlock.sql (common)
CREATE TABLE SHEDLOCK (
  NAME       VARCHAR(64)  NOT NULL PRIMARY KEY,
  LOCK_UNTIL TIMESTAMP    NOT NULL,
  LOCKED_AT  TIMESTAMP    NOT NULL,
  LOCKED_BY  VARCHAR(255) NOT NULL
);
```

**Seed data.** `R__seed_va_reference.sql` inserts state `VA` (Virginia), all 133 Virginia county and independent-city FIPS codes from the US Census Bureau ANSI/FIPS code list (verify every row against that list), and four placeholder categories: `CAT1` to `CAT4`, named "Category 1" to "Category 4", display order 1 to 4.

**Development data.** `DevDataSeeder` (active only in the `h2` profile) inserts a configurable number of records, default 5,000, spread unevenly over about 20 FIPS codes. It must include one FIPS code with an empty category and one large FIPS code for paging tests. A property `reporting.dev.seed-records` controls the count, so the 100,000-record performance test can reuse it.

**JPA mapping.** `ReportRun` and `ReportDocument` are entities with `@Version` on `VERSION` and `GenerationType.IDENTITY` keys. `REPORT_CONTENT` is never mapped by JPA; `ReportContentStore` streams it with JDBC `setBinaryStream` and `getBinaryStream`.

**DB2 hand-off for the DBA.** Put `REPORT_CONTENT` in a dedicated LOB tablespace, with no inline LOB length and no compression (PDFs are already compressed). Confirm the source table has an index matching `IX_RPT_RECORD_REPORT` once the real table is known.

## 8. Report processing flow

A run moves through three stages, each owned by a different component: run creation, generation, and sending. Only run creation is tied to the node that received the trigger.

**Stage A — Create the run** (`ReportRunService.startRun`, one transaction)

- Validate the state is active and has a profile. Validate any FIPS filter: each code matches `^\d{5}$` and belongs to the state.
- Insert `REPORT_RUN` with status RUNNING, `DATA_AS_OF` = database time, and the trigger type and requester.
- Acquire the state lock: `UPDATE RPT_STATE SET ACTIVE_RUN_ID = ? WHERE STATE_CODE = ? AND ACTIVE_RUN_ID IS NULL`. Zero rows updated means another run is active: roll back and return HTTP 409 (API) or log and skip (scheduler).
- Run the workload query (`sql/fips-workload.sql`):

```
SELECT r.FIPS_CODE, COUNT(*) AS RECORD_COUNT,
       MAX(CASE WHEN f.FIPS_CODE IS NULL THEN 1 ELSE 0 END) AS UNKNOWN_FIPS
FROM RPT_RECORD r
LEFT JOIN RPT_FIPS f ON f.FIPS_CODE = r.FIPS_CODE AND f.STATE_CODE = r.STATE_CODE AND f.ACTIVE = 1
WHERE r.STATE_CODE = ?
GROUP BY r.FIPS_CODE
FOR READ ONLY
```

Apply the FIPS filter in Java if present. FIPS codes with no rows never appear, which satisfies FR-02.

- Insert one `REPORT_DOCUMENT` per FIPS code with status PENDING. A FIPS code flagged `UNKNOWN_FIPS` is inserted as DATA_ERROR with the message `FIPS code not in RPT_FIPS for state`.
- Set `TOTAL_DOCUMENTS`. If zero documents were created, set the run to COMPLETED and release the state lock immediately.

**Stage B — Generate** (`GenerationPoller` and `GenerationWorker`, on every node)

- The poller claims up to as many PENDING or due GENERATION_FAILED documents as it has free worker threads (section 9 claim protocol), then hands each to a worker.
- The worker opens one streaming read (`sql/report-rows.sql`):

```
SELECT c.DISPLAY_ORDER, r.CATEGORY_CODE, r.CASE_NUMBER, r.LAST_NAME, r.FIRST_NAME,
       r.EVENT_DATE, r.AMOUNT, r.STATUS, r.DESCRIPTION
FROM RPT_RECORD r
LEFT JOIN RPT_CATEGORY c ON c.STATE_CODE = r.STATE_CODE AND c.CATEGORY_CODE = r.CATEGORY_CODE AND c.ACTIVE = 1
WHERE r.STATE_CODE = ? AND r.FIPS_CODE = ?
ORDER BY c.DISPLAY_ORDER, r.LAST_NAME, r.FIRST_NAME, r.CASE_NUMBER
FOR READ ONLY
```

Fetch size 500, `RowCallbackHandler`, no list of rows. A row with a null `DISPLAY_ORDER` (unknown category) stops generation with DATA_ERROR.

- `FipsReportComposer` (Template Method) drives the writer: open the document with the title block and reserved summary area, then for each active category in display order emit `beginSection`, its rows or the empty message, and `endSection`. Categories with no rows are emitted when the stream passes their position, and any remaining categories after the stream ends.
- `SummaryCollector` counts rows per category during the stream. `finish` draws the summary into the reserved area, stamps headers and footers, adds bookmarks, and saves the PDF to a temp file.
- In one transaction: stream the temp file into `REPORT_CONTENT`, set status GENERATED with record count, page count, size, SHA-256, and `GENERATED_AT`, and clear the lease. Delete the temp file in a `finally` block.
- After commit, publish `DocumentGeneratedEvent`. A `@TransactionalEventListener(AFTER_COMMIT)` wakes the local send poller so delivery starts without waiting for the next poll.

**Stage C — Send** (`SendPoller` and `SendWorker`, on every node)

- The poller claims GENERATED documents and due SEND_FAILED documents, up to its free send slots.
- Before sending, check FR-15: if a newer document for the same state and FIPS code is GENERATED, SENDING, or SENT, set this one to SUPERSEDED and stop.
- Load the PDF bytes from `REPORT_CONTENT` and verify the SHA-256. A mismatch or missing content is a permanent failure.
- Send through `ReportTransmitter` (section 13), with in-process retries for transient errors.
- On success, set SENT, `SENT_AT`, and `JMS_MESSAGE_ID`. On failure, apply the classification and backoff in section 17.

**Run completion** (`RunFinalizer`, ShedLock-protected)

- When no document of a RUNNING run is PENDING, GENERATING, or GENERATION_FAILED, set the run to DISPATCHING and release the state lock (`ACTIVE_RUN_ID = NULL`). A new run for the state may now start, while sends of this run continue.
- When every document of a DISPATCHING run is SENT, SUPERSEDED, DATA_ERROR, GENERATION_FAILED_FINAL, or SEND_FAILED_FINAL, set the run to COMPLETED if all are SENT or SUPERSEDED, otherwise COMPLETED_WITH_ERRORS, and set `COMPLETED_AT`.

## 9. Document state machine and multi-node coordination

Every document status change goes through `DocumentStatus.canTransitionTo` and a conditional UPDATE guarded by `VERSION`, so two nodes can never both act on the same document.

**Document statuses.** PENDING, GENERATING, GENERATION_FAILED, GENERATION_FAILED_FINAL, DATA_ERROR, GENERATED, SENDING, SEND_FAILED, SEND_FAILED_FINAL, SENT, SUPERSEDED. Final statuses: GENERATION_FAILED_FINAL, DATA_ERROR, SEND_FAILED_FINAL, SENT, SUPERSEDED; the only exit from a final status is a manual retransmit from SEND_FAILED_FINAL.

| From | To | Trigger | Component |
|---|---|---|---|
| (new) | PENDING | Run created, FIPS code known | ReportRunService |
| (new) | DATA_ERROR | Run created, FIPS code unknown | ReportRunService |
| PENDING | GENERATING | Claimed; lease set | GenerationPoller |
| GENERATION_FAILED | GENERATING | Claimed after `NEXT_ATTEMPT_AT` | GenerationPoller |
| GENERATING | GENERATED | PDF stored | GenerationWorker |
| GENERATING | DATA_ERROR | Unknown category or other data problem | GenerationWorker |
| GENERATING | GENERATION_FAILED | Transient error, attempts left; backoff set | GenerationWorker |
| GENERATING | GENERATION_FAILED_FINAL | Permanent error, or attempts used up | GenerationWorker |
| GENERATING | GENERATION_FAILED or GENERATION_FAILED_FINAL | Lease expired (node died); counts as an attempt | LeaseRecoverySweeper |
| GENERATED | SENDING | Claimed; lease set | SendPoller |
| SEND_FAILED | SENDING | Claimed after `NEXT_ATTEMPT_AT` | SendPoller |
| SENDING | SUPERSEDED | Newer document exists (FR-15) | SendWorker |
| SENDING | SENT | Broker accepted the message | SendWorker |
| SENDING | SEND_FAILED | Transient error, attempts left; backoff set | SendWorker |
| SENDING | SEND_FAILED_FINAL | Permanent error, or attempts used up | SendWorker |
| SENDING | SEND_FAILED | Lease expired; the message may have been sent, so a duplicate is possible and allowed (NFR-08) | LeaseRecoverySweeper |
| SEND_FAILED or SEND_FAILED_FINAL | SEND_FAILED with `NEXT_ATTEMPT_AT` = now and attempts reset | Manual retransmit (refused if SUPERSEDED or content purged) | API |

**Run statuses.** RUNNING (generation phase; holds the state lock) → DISPATCHING (all documents generated or failed; lock released) → COMPLETED or COMPLETED_WITH_ERRORS. FAILED is used only when Stage A fails after the run row is committed, which cannot happen if Stage A is one transaction; keep the status for safety.

**Claim protocol** (`DocumentClaimRepository`, plain JDBC, identical on H2 and DB2)

- Read the database time once per poll with `DbClock` (query from config: H2 `SELECT CURRENT_TIMESTAMP`, DB2 `SELECT CURRENT TIMESTAMP FROM SYSIBM.SYSDUMMY1`).
- Select candidates: `SELECT DOC_ID, VERSION FROM REPORT_DOCUMENT WHERE STATUS IN (?, ?) AND (NEXT_ATTEMPT_AT IS NULL OR NEXT_ATTEMPT_AT <= ?) ORDER BY CREATED_AT FETCH FIRST ? ROWS ONLY`.
- For each candidate: `UPDATE REPORT_DOCUMENT SET STATUS = ?, LEASE_OWNER = ?, LEASE_EXPIRES_AT = ?, VERSION = VERSION + 1, UPDATED_AT = ? WHERE DOC_ID = ? AND VERSION = ?`. One row updated means this node owns it; zero means another node won, so skip it.
- `LEASE_EXPIRES_AT` = database time + lease duration, computed in Java and passed as a parameter. Never use vendor interval syntax.

**Lease recovery** (`LeaseRecoverySweeper`, every 60 s, ShedLock): find GENERATING or SENDING rows whose `LEASE_EXPIRES_AT` is before database time and apply the lease-expired transitions above.

**Lease length.** Generation lease (default 15 min) must exceed the slowest expected render; the worker logs a warning at 80% of the lease. Send lease defaults to 5 min.

**Locks summary.** ShedLock guards only singleton jobs: the per-state schedulers, `LeaseRecoverySweeper`, `RunFinalizer`, and `ContentPurgeJob`. Pollers run on every node without ShedLock, because the claim UPDATE already serializes them. The per-state run lock is the `RPT_STATE.ACTIVE_RUN_ID` column.

## 10. PDF framework (report-pdf-core) design

`report-pdf-core` is a streaming, two-pass tabular report engine over PDFBox 3.0: pass 1 lays out the body as rows arrive, pass 2 stamps the summary, headers, footers, and bookmarks once the page count is known.

**Public API** (the only public types; everything else is package-private)

```
public interface ReportDocumentWriter extends AutoCloseable {
    /** Starts a category section: heading, bookmark, table header row. */
    void beginSection(SectionSpec section);
    /** Adds one data row; cells in column order. Never splits a row across pages. */
    void addRow(List<CellValue> cells);
    /** Renders the empty-category message under the table header. */
    void addEmptyMessage(String message);
    void endSection();
    /** Draws the summary into the area reserved on page 1, runs pass 2, saves. */
    RenderResult finish(SummaryData summary, OutputStream out) throws IOException;
    @Override void close();   // releases PDFBox resources and temp files; safe after finish
}

public final class ReportDocumentWriters {
    public static ReportDocumentWriter open(DocumentSpec spec, Path workDir) throws IOException;
}
```

- `DocumentSpec` (builder): page size and margins, `FontSet` (regular, bold, italic as byte arrays), logo bytes and alt text, title-block lines, running-header text, footer disclaimer, report ID, metadata (title, subject, author, language `en-US`), summary row count, tagging on or off.
- `SectionSpec` (builder): heading text, continuation label, `TableSpec` (list of `ColumnSpec`: header label, width ratio, `Alignment`, wrap allowed).
- `CellValue`: already-formatted display text plus alignment override. Formatting is the service's job (`CellFormatter`), so the core never knows about dates or currency.
- `RenderResult`: page count, rows written, and per-section first page (used for bookmarks and tests).
- A writer is single-threaded and single-use. Each worker thread opens its own.

**Pass 1 — body layout rules**

- Create the document with `new PDDocument(IOUtils.createTempFileOnlyStreamCache())` so page content lives in temp files, not heap.
- Load fonts per document with `PDType0Font.load(document, new ByteArrayInputStream(bytes), true)` (subset embedding). Font bytes are cached once by the service; PDFBox font objects are never shared across documents or threads.
- Page 1 reserves the title block at the top and the summary area below it. Summary height = heading + (summary row count + 2) × row height; it is known before any data arrives.
- Body area on every page = page height − margins − header region − footer region. Header region: title block on page 1, running header on pages 2+.
- For each row: wrap each cell with `TextWrapper` to its column width; row height = max line count × leading + vertical padding. If the row does not fit below the cursor, start a new page, draw the continuation label and repeated header row (both as artifacts), then the row.
- A row taller than a full body area is cut to fit, its last visible line ends in an ellipsis, and a warning is logged with the FIPS code only.
- Keep-together: a section heading must fit together with the header row and the first data row (or the empty message); otherwise the section starts on a new page.
- Columns marked no-wrap are truncated with an ellipsis instead of wrapping.
- `GlyphSanitizer` replaces control characters and any character the font cannot render with `?` before measuring, so PDFBox never throws on unusual input.

**Pass 2 — decoration (**`PageDecorator`**)**

- `SummaryPainter` draws the summary table into the reserved rectangle on page 1 (append mode).
- For every page: page 1 keeps its title block; pages 2+ get `RunningHeaderPainter`. Every page gets `FooterPainter` with the disclaimer, report ID, and "Page X of Y". Headers and footers are artifacts of type Pagination.
- `BookmarkBuilder` adds outline entries: "Summary", then one per category pointing to its first page.
- Save with PDFBox default compression; return `RenderResult`.

**Tagging (**`TaggingContext`**).** Layout code calls `beginStructure`, `markContent`, `endStructure`, and `beginArtifact` at the right places. `NoOpTaggingContext` does nothing (Phases 1–4). `PdfUaTaggingContext` (Phase 5) builds the structure tree in section 12. Pass 2 can attach marked content to structure elements that pass 1 created, so the summary stays first in reading order even though it is drawn last.

## 11. Report layout and formatting

Every report uses one template: US Letter landscape (792 × 612 pt), 36 pt (0.5 in) margins, Noto Sans, black text on white. Only text, logo, and columns vary by state. All sizes below are defaults held in `DocumentSpec`.

| Element | Where | Content | Style |
|---|---|---|---|
| Title block | Page 1 top, about 96 pt tall | Logo at left (max 54 pt high, 144 pt wide, aspect kept). Right of logo: title; subtitle (state name); "FIPS 51059 – Fairfax County"; "Data as of 11/10/2026 02:00 AM ET"; "Report ID R-1702-48213" | Title 16 pt bold; subtitle 11 pt; FIPS line 12 pt bold; other lines 9 pt. 0.75 pt rule below |
| Running header | Pages 2+, 28 pt tall | Left: "{title} – FIPS {code} {locality}". Right: "Data as of …" | 9 pt bold left, 9 pt right; 0.5 pt rule below |
| Footer | Every page, 28 pt tall | Left: disclaimer (max 2 lines, then ellipsis). Center: report ID. Right: "Page X of Y" | 8 pt disclaimer, 9 pt others; 0.5 pt rule above |
| Summary | Page 1 under the title block | Heading "Summary"; table with columns Category and Records; one row per active category including zeros; bold Total row | Heading 12 pt bold; table as below; counts right-aligned |
| Section heading | Start of each category | "Category 1 – {display name} ({n} records)" | 12 pt bold; 12 pt space above, 6 pt below |
| Continuation label | Top of body on continuation pages | "{display name} (continued)" | 10 pt italic; artifact |
| Table header row | Each section; repeated on every continuation page | Column labels | 9 pt bold; fill #E6E6E6; 0.5 pt borders #808080 |
| Data rows | Table body | Formatted cells | 9 pt, leading 11 pt, padding 3 pt horizontal and 2 pt vertical; even rows filled #F5F5F5 |
| Empty category | Under the header row | "No content to display in this category." (configurable) | 9 pt italic, 4 pt above |

**Value formatting** (`CellFormatters` in the service)

| Column type | Format | Alignment | Wrap |
|---|---|---|---|
| TEXT | As stored, trimmed | Left | Configurable per column |
| DATE | `MM/dd/yyyy` | Left | No |
| CURRENCY | `$#,##0.00`, negatives as `-$1,234.00` | Right | No |
| INTEGER | `#,##0` | Right | No |
| Null value | Empty cell | — | — |

**Default Virginia columns** (width ratios sum to 100)

| Key | Label | Type | Width | Wrap |
|---|---|---|---|---|
| caseNumber | Case Number | TEXT | 14 | No |
| lastName | Last Name | TEXT | 14 | Yes |
| firstName | First Name | TEXT | 14 | Yes |
| eventDate | Event Date | DATE | 10 | No |
| amount | Amount | CURRENCY | 11 | No |
| status | Status | TEXT | 10 | No |
| description | Description | TEXT | 27 | Yes |

**Sort order** within a category: last name, first name, case number. **File name** for downloads: `{STATE}_{FIPS}_{yyyyMMdd'T'HHmm}_{docId}.pdf` using the data-as-of time.

**Contrast.** All text is black (#000000). The lightest fill (#F5F5F5) and the header fill (#E6E6E6) both keep contrast well above WCAG AA and print legibly in grayscale. Color never carries meaning on its own.

## 12. Accessibility requirements

Every PDF must pass veraPDF for both PDF/UA-1 and PDF/A-2a, and be readable in a logical order by a screen reader. PDFBox has no high-level tagging API, so `PdfUaTaggingContext` builds the structure by hand; this is the most intricate code in the project and gets its own phase.

**Document-level requirements**

- Structure tree root with a parent tree and `ParentTreeNextKey`; `MarkInfo /Marked true`.
- Document language `en-US`; `ViewerPreferences /DisplayDocTitle true`; document title in the info dictionary and XMP `dc:title`.
- XMP metadata declaring `pdfaid:part=2`, `pdfaid:conformance=A`, and `pdfuaid:part=1`.
- Output intent with the bundled sRGB ICC profile.
- All fonts embedded (subset). No encryption, no JavaScript, no transparency groups on the logo.
- Every page has `/Tabs /S` (tab order follows structure).

**Structure tree**

```
Document
├── H1        report title (title block text is tagged; the rest of the title block lines are P)
├── Figure    logo, with Alt text from the profile
├── Sect      Summary
│   ├── H2    "Summary"
│   └── Table → THead → TR → TH (Scope Column) ; TBody → TR → TD ; total row TR → TH (Scope Row) + TD
└── Sect      one per category, in display order
    ├── H2    section heading
    ├── Table → THead → TR → TH (Scope Column) ; TBody → TR → TD   (rows may span pages; each element records its page)
    └── P     empty-category message, when present
```

**Artifacts (never read aloud):** running headers, footers, page numbers, horizontal rules, row fills and borders, continuation labels, and the repeated table header rows on continuation pages. The first header row of each table is real content (TH); its repeats are artifacts.

**Bookmarks:** "Summary" and one per category, matching the H2 headings.

**Verification:** automated veraPDF tests on sample reports (empty category, multi-page category, single row). Before go-live, one manual check with a screen reader (NVDA with Adobe Acrobat Reader) and the PAC accessibility checker, recorded in `docs/DECISIONS.md`.

## 13. Transmission via WebSphere SIB

Each PDF goes to one SIB queue as a persistent `BytesMessage` in a transacted JMS session, with metadata as JMS properties. The broker accepting the message counts as SENT.

**Connection (profile** `sib`**).** `SibConnectionFactoryConfig` builds the factory with the thin client and wraps it in `UserCredentialsConnectionFactoryAdapter` and then `CachingConnectionFactory`. Phase 0 must confirm the exact thin-client calls against its Javadoc; the expected shape is:

```
JmsConnectionFactory cf = JmsFactoryFactory.getInstance().createConnectionFactory();
cf.setBusName(props.getBusName());
cf.setProviderEndpoints(props.getProviderEndpoints());      // "host1:7276:BootstrapBasicMessaging,host2:7276:BootstrapBasicMessaging"
cf.setTargetTransportChain(props.getTargetTransportChain()); // "InboundBasicMessaging"
Queue queue = JmsFactoryFactory.getInstance().createQueue(props.getQueueName());
```

In the `h2` profile and in tests, `ArtemisDevConfig` uses an embedded ActiveMQ Artemis broker instead, so the app runs locally without SIB. Only these two config classes know which broker is in use.

**JmsTemplate settings:** `sessionTransacted = true`, `explicitQosEnabled = true`, `deliveryMode = PERSISTENT`, no time-to-live. Use only JMS 1.1 methods.

**Message contract** (version 1; share with the downstream team)

| JMS property | Type | Example | Meaning |
|---|---|---|---|
| `reportDocId` | long | 48213 | Idempotency key; downstream de-duplicates on it |
| `runId` | long | 1702 | Run that produced the document |
| `stateCode` | String | VA | State |
| `fipsCode` | String | 51059 | FIPS code |
| `generatedAt` | String (ISO-8601 with offset) | 2026-11-10T02:03:11-05:00 | When the PDF was created; downstream keeps the latest per FIPS code |
| `dataAsOf` | String (ISO-8601 with offset) | 2026-11-10T02:00:00-05:00 | Data snapshot time printed on the report |
| `recordCount` | int | 1240 | Rows in the report |
| `pageCount` | int | 31 | Pages |
| `sha256` | String (hex) | 9f86d0… | Content checksum |
| `contentType` | String | application/pdf | Body type |
| `schemaVersion` | int | 1 | Contract version |

The message body is the raw PDF bytes. No names or other record data go into properties (NFR-13).

**SendWorker steps:** acquire a permit from the per-node send semaphore (default 2), load bytes from `REPORT_CONTENT`, verify SHA-256, build the message, send with Spring Retry (3 attempts, backoff 2 s × 2 up to 10 s) for transient errors, commit, record `JMSMessageID`, release the permit in `finally`.

**Phase 0 must prove:** connection from Tomcat 9 on the target JDK, a 25 MB message accepted by the SIB destination, properties visible to the downstream consumer, and the list of IBM jars actually required.

## 14. REST API

All endpoints live under `/api/v1`, use JSON, and are asynchronous: a request that starts work returns 202 with a link to poll. Until security is added, the caller's identity comes from the optional `X-Requested-By` header (default `anonymous`).

| Method | Path | Success | Errors | Purpose |
|---|---|---|---|---|
| POST | `/report-runs` | 202 | 400, 404 (unknown state), 409 (run active) | Start a run |
| GET | `/report-runs?stateCode=VA&page=0&size=20` | 200 | 400 | List runs, newest first |
| GET | `/report-runs/{runId}` | 200 | 404 | Run with document counts by status |
| GET | `/report-runs/{runId}/documents?status=SEND_FAILED&page=0&size=50` | 200 | 404 | Documents of a run |
| POST | `/report-runs/{runId}/retransmit-failed` | 202 | 404 | Re-queue all SEND_FAILED and SEND_FAILED_FINAL documents of the run |
| GET | `/report-documents/{docId}` | 200 | 404 | Document detail |
| GET | `/report-documents/{docId}/content` | 200 `application/pdf` | 404, 410 (purged) | Download the stored PDF |
| POST | `/report-documents/{docId}/retransmit` | 202 | 404, 409 (superseded or wrong status), 410 (purged) | Re-queue one document |

**Start a run**

```
POST /api/v1/report-runs
{ "stateCode": "VA", "fipsCodes": ["51059", "51013"] }      // fipsCodes optional

202 Accepted   Location: /api/v1/report-runs/1702
{ "runId": 1702, "stateCode": "VA", "status": "RUNNING", "triggerType": "API",
  "dataAsOf": "2026-11-10T14:05:00-05:00", "totalDocuments": 2 }
```

**Run detail**

```
GET /api/v1/report-runs/1702
{ "runId": 1702, "stateCode": "VA", "status": "DISPATCHING", "triggerType": "API",
  "requestedBy": "jdoe", "dataAsOf": "2026-11-10T14:05:00-05:00",
  "createdAt": "2026-11-10T14:05:00-05:00", "completedAt": null, "totalDocuments": 2,
  "documentsByStatus": { "SENT": 1, "SEND_FAILED": 1 } }
```

**Document detail** fields: `docId`, `runId`, `stateCode`, `fipsCode`, `localityName`, `status`, `recordCount`, `pageCount`, `sizeBytes`, `sha256`, `generationAttempts`, `sendAttempts`, `nextAttemptAt`, `lastError`, `generatedAt`, `sentAt`, `contentAvailable`.

**Errors** use one shape (`ApiProblem`, modeled on RFC 7807), produced only by `ApiExceptionHandler`:

```
{ "type": "https://example.com/problems/run-already-active", "title": "Run already active",
  "status": 409, "detail": "State VA already has run 1701 in progress.", "instance": "/api/v1/report-runs" }
```

Validation uses Bean Validation on request DTOs. Timestamps are ISO-8601 with offset in the business time zone. Paging uses `page` and `size` with a maximum size of 200.

## 15. Scheduling and background jobs

Seven jobs run on every node; four of them use ShedLock so only one node executes each run. All use a dedicated `ThreadPoolTaskScheduler` (pool size 6) so a slow job never delays the others.

| Job | Default schedule | ShedLock | What it does |
|---|---|---|---|
| `DailyReportScheduler` | Per state cron, default `0 0 2 * * *`, zone America/New_York | Yes, name `daily-report-{STATE}`, lockAtMostFor 30 min, lockAtLeastFor 1 min | Calls `ReportRunService.startRun` with trigger SCHEDULED. A 409-equivalent outcome is logged at WARN, not raised. |
| `GenerationPoller` | Fixed delay 5 s | No | Claims PENDING and due GENERATION_FAILED documents up to free generation workers |
| `SendPoller` | Fixed delay 5 s, plus wake-up on `DocumentGeneratedEvent` | No | Claims GENERATED and due SEND_FAILED documents up to free send permits |
| `LeaseRecoverySweeper` | Fixed delay 60 s | Yes | Resolves expired leases (section 9) |
| `RunFinalizer` | Fixed delay 30 s | Yes | Moves runs RUNNING → DISPATCHING → COMPLETED or COMPLETED_WITH_ERRORS; releases the state lock |
| `ContentPurgeJob` | `0 30 3 * * *` | Yes | Deletes `REPORT_CONTENT` rows 90+ days after `SENT_AT`, 500 per transaction; sets `CONTENT_PURGED_AT` |
| `TempFileJanitor` | Once at startup | No | Deletes files older than 1 hour in this node's temp directory |

**Per-state cron registration.** `DailyReportScheduler` implements `SchedulingConfigurer` and registers one `CronTrigger` per enabled state from `reporting.states`. Because lock names are dynamic, it wraps each task with ShedLock's `LockingTaskExecutor` instead of the `@SchedulerLock` annotation.

**Executors.** Generation workers: `ThreadPoolTaskExecutor`, core = max = `reporting.generation.workers` (default 4), queue capacity 0; the poller never claims more than the free threads. Sends: `ThreadPoolTaskExecutor` sized `reporting.transmission.workers` (default 2), with a matching semaphore around the byte-array load. On shutdown, executors wait up to 60 s; unfinished work is recovered by lease expiry.

## 16. Configuration reference

All settings bind to `ReportingProperties` (`@ConfigurationProperties("reporting")`, `@Validated`), so a wrong value stops startup. Production runs with profiles `db2,sib,prod`; local development uses `h2`.

```
# application.yml (defaults for all profiles)
reporting:
  node-id: ${HOSTNAME:local-node}            # lease owner name; must be unique per node
  business-zone: America/New_York
  db:
    now-query: SELECT CURRENT_TIMESTAMP      # overridden in application-db2.yml
  generation:
    workers: 4
    poll-interval: 5s
    lease-duration: 15m
    max-attempts: 3
    backoff: { initial: 1m, multiplier: 2.0, max: 30m }
    fetch-size: 500
    temp-dir: ${java.io.tmpdir}/fips-reports
  transmission:
    workers: 2
    poll-interval: 5s
    lease-duration: 5m
    in-process: { attempts: 3, initial: 2s, multiplier: 2.0, max: 10s }
    max-attempts: 8
    backoff: { initial: 1m, multiplier: 2.0, max: 1h }
    queue-name: REPORT.OUT.QUEUE
  retention:
    content-days: 90
    purge-cron: "0 30 3 * * *"
  fonts:
    regular: classpath:fonts/NotoSans-Regular.ttf
    bold: classpath:fonts/NotoSans-Bold.ttf
    italic: classpath:fonts/NotoSans-Italic.ttf
  color-profile: classpath:color/sRGB.icc
  tagging-enabled: false                     # true from Phase 5
  states:
    VA:
      enabled: true
      cron: "0 0 2 * * *"
      title: "Commonwealth of Virginia – Locality Report"
      subtitle: "Virginia"
      footer-disclaimer: "Official use only. Generated automatically; do not edit."
      empty-category-message: "No content to display in this category."
      logo:
        resource: classpath:branding/placeholder-logo.png
        alt-text: "Placeholder agency logo"
      columns:
        - { key: caseNumber,  label: "Case Number", type: TEXT,     width: 14, wrap: false }
        - { key: lastName,    label: "Last Name",   type: TEXT,     width: 14, wrap: true }
        - { key: firstName,   label: "First Name",  type: TEXT,     width: 14, wrap: true }
        - { key: eventDate,   label: "Event Date",  type: DATE,     width: 10, wrap: false }
        - { key: amount,      label: "Amount",      type: CURRENCY, width: 11, wrap: false }
        - { key: status,      label: "Status",      type: TEXT,     width: 10, wrap: false }
        - { key: description, label: "Description", type: TEXT,     width: 27, wrap: true }
      category-columns: {}                   # optional: categoryCode -> column list override

spring:
  flyway:
    locations: classpath:db/migration/common,classpath:db/migration/{vendor}
  jpa:
    open-in-view: false
    hibernate.ddl-auto: validate
  datasource.hikari:
    maximum-pool-size: 12                    # generation workers + send workers + 6
    leak-detection-threshold: 20m            # above the generation lease
  task.scheduling.pool.size: 6
```

```
# application-h2.yml
spring.datasource.url: jdbc:h2:file:./data/reportdb;MODE=DB2;DATABASE_TO_UPPER=TRUE;AUTO_SERVER=TRUE
spring.artemis: { mode: embedded }
reporting.dev.seed-records: 5000

# application-db2.yml
spring.datasource:
  url: jdbc:db2://${DB2_HOST}:${DB2_PORT}/${DB2_DATABASE}
  username: ${DB2_USER}
  password: ${DB2_PASSWORD}
  driver-class-name: com.ibm.db2.jcc.DB2Driver
reporting.db.now-query: SELECT CURRENT TIMESTAMP FROM SYSIBM.SYSDUMMY1

# application-sib.yml
reporting.sib:
  bus-name: ${SIB_BUS}
  provider-endpoints: ${SIB_ENDPOINTS}       # host:7276:BootstrapBasicMessaging[,...]
  target-transport-chain: InboundBasicMessaging
  username: ${SIB_USER:}
  password: ${SIB_PASSWORD:}

# application-prod.yml
management.endpoints.web.exposure.include: health,info,metrics,prometheus
logging.level.com.example.reporting: INFO
```

**Startup validation (**`ProfileValidator`**)** fails startup when any of these is true: an enabled state has no row in `RPT_STATE`; an active state row has no profile; column widths do not sum to 100; a column key is not produced by `ReportRowMapper`; a logo resource is missing or has no alt text; `generation.lease-duration` is 5 minutes or less.

**JVM settings for Tomcat (**`bin/setenv.sh`**)**

```
CATALINA_OPTS="$CATALINA_OPTS -Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/tomcat \
  -Xlog:gc*:file=/var/log/tomcat/gc.log:time,uptime:filecount=5,filesize=20m \
  -Djava.io.tmpdir=/data/tmp -Dspring.profiles.active=db2,sib,prod"
```

Reserve free disk in the temp directory of at least 3 × the largest expected PDF × generation workers (about 250 MB with defaults).

## 17. Error handling and retry

Every failure is classified once, at the boundary where it happens, as transient (retry later) or permanent (stop and alert). One document's failure never affects another document or the run.

**Exception hierarchy** (in `com.example.reporting`, unchecked)

```
ReportingException (abstract; boolean isRetryable())
├── TransientGenerationException      → GENERATION_FAILED (until max attempts)
├── ReportDataException               → DATA_ERROR (never retried)
├── RenderingException                → GENERATION_FAILED_FINAL
├── TransientTransmissionException    → SEND_FAILED (until max attempts)
└── PermanentTransmissionException    → SEND_FAILED_FINAL
```

**Classification**

| Source | Transient | Permanent |
|---|---|---|
| Database during generation | `TransientDataAccessException`, `RecoverableDataAccessException`, `QueryTimeoutException`, `CannotGetJdbcConnectionException` | `BadSqlGrammarException`, other `NonTransientDataAccessException` |
| Data content | — | Unknown FIPS code, unknown category (`ReportDataException`) |
| PDF rendering | Temp-disk full (`IOException` with no space) | Any other PDFBox or layout error |
| JMS send | Any `JMSException` not listed as permanent, `UncategorizedJmsException` | `JMSSecurityException`, `InvalidDestinationException`, `MessageFormatException` |
| Stored content | — | Content missing, purged, or SHA-256 mismatch |

**Backoff** (`Backoff`): delay = min(initial × multiplier^(attempt − 1), max), plus random jitter of ±10%. `NEXT_ATTEMPT_AT` = database time + delay. With defaults, generation retries after about 1, 2, then gives up at attempt 3; sends retry after about 1, 2, 4, 8, 16, 32, then 60 minutes, and the eighth failed attempt becomes SEND_FAILED_FINAL.

**Recording errors.** `LAST_ERROR` stores the exception class and message, truncated to 2,000 characters, with no stack trace and no record data. Full stack traces go to the application log at ERROR with MDC context.

**Run-level failures.** If Stage A fails, its transaction rolls back and the API returns 500 (scheduler logs ERROR); no partial run remains. A failure in one poller cycle is logged and the next cycle continues.

**Interrupted work.** On JVM shutdown or crash, leases expire and section 9 recovery applies. Workers check `Thread.interrupted()` between rows and between sends, and release resources in `finally` blocks.

## 18. Observability and operations

Operators must be able to answer "did today's reports go out, and if not, which ones and why" from metrics and the API alone, without reading logs.

**Metrics (Micrometer)**

| Name | Type | Tags | Meaning |
|---|---|---|---|
| `reporting.runs.started` | Counter | state, trigger | Runs created |
| `reporting.documents.generated` | Counter | state, outcome (success, data_error, failed) | Generation results |
| `reporting.generation.duration` | Timer | state | Time to render and store one PDF |
| `reporting.pdf.pages` | Distribution summary | state | Pages per PDF |
| `reporting.pdf.bytes` | Distribution summary | state | Size per PDF |
| `reporting.send.duration` | Timer | state, outcome | Time to send one message |
| `reporting.send.failures` | Counter | state, classification (transient, permanent) | Send failures |
| `reporting.documents.by.status` | Gauge (refreshed every 60 s by `RunFinalizer`'s node) | status | Documents per status across the cluster |
| `reporting.leases.recovered` | Counter | kind (generation, send) | Expired leases recovered |

**Health.** Actuator `health` includes the database and a JMS check (`JmsHealthIndicator` on the configured connection factory). Readiness fails if Flyway has not finished.

**Logging.** SLF4J with Logback. Every log line inside a worker carries MDC keys `nodeId`, `runId`, `docId`, `stateCode`, `fipsCode`. Never log record values (NFR-13). Log run start and completion at INFO with counts; log each document status change at DEBUG; log failures at WARN (transient) or ERROR (permanent).

**Alerts to configure** (in the monitoring tool)

- Any increase in documents with status SEND_FAILED_FINAL, GENERATION_FAILED_FINAL, or DATA_ERROR.
- The scheduled run for a state has not reached DISPATCHING by 04:00 America/New_York.
- `reporting.leases.recovered` increases (a node died or a lease is too short).
- JMS health DOWN for more than 5 minutes.

**Runbook (**`docs/RUNBOOK.md`**, written in Phase 6)** covers: checking today's run, retransmitting one document or a whole run, what DATA_ERROR means and who fixes the data, a run stuck in RUNNING (check leases and `RPT_STATE.ACTIVE_RUN_ID`), clearing a stale state lock safely, switching from H2 to DB2, and rotating SIB credentials.

## 19. Coding standards

Code should read as plain, explicit Java 11 that a new team member can follow without framework magic.

**Design**

- Single responsibility per class; keep classes under about 300 lines and methods under about 40.
- Depend on interfaces at package boundaries (`ReportDataProvider`, `ReportTransmitter`, `ReportDocumentWriter`, `TaggingContext`).
- Value objects are `final`, immutable, built with a static nested `Builder`, and validate in the constructor (`Objects.requireNonNull`, range checks).
- Classes are `final` unless designed for extension. `FipsReportComposer` is the only intended extension point (protected hook methods for state-specific sections).
- Use `java.time` only, with an injected `Clock`. Never call `LocalDateTime.now()` without a clock; database time comes from `DbClock`.
- `Optional` only as a return type. No `null` returns from public methods.
- No static mutable state. No `synchronized` on shared caches; use immutable snapshots built at startup.

**Spring**

- Constructor injection only; no `@Autowired` on fields.
- `@Transactional` only on service methods, never on controllers or repositories. Keep transactions short: never hold one open while rendering a PDF or sending a message.
- `spring.jpa.open-in-view=false`.
- Configuration through typed `@ConfigurationProperties` classes; no `@Value` sprinkled through code.

**SQL and data**

- All SQL in `src/main/resources/sql/*.sql` or repository methods, with bind parameters only. No string concatenation of values.
- Every report query ends with `FOR READ ONLY`.
- Use `FETCH FIRST n ROWS ONLY` for limits; it works on both H2 and DB2.

**Style and quality**

- Formatting: Google Java Style with 4-space indentation and 120-character lines, enforced by Checkstyle.
- SpotBugs at default threshold with no suppressions unless justified in a comment.
- Javadoc on every public type and method in report-pdf-core, and on every public service method.
- Commit messages: `Phase N: <what changed>`.
- Record any decision the spec does not cover in `docs/DECISIONS.md`.

## 20. Testing strategy

Pagination is tested as pure math before rendering, behavior is tested end to end on H2 with embedded Artemis, and compliance is tested by veraPDF; `mvn verify` runs everything except the separate perf and DB2 profiles.

| Level | What is tested | Tools |
|---|---|---|
| Unit — layout | Row height from wrapped text; page breaks; header repeat count; keep-together; oversized-row truncation; no-wrap ellipsis; summary height reservation | JUnit 5, AssertJ |
| Unit — text | Wrapping on spaces and long words; `GlyphSanitizer` on control characters and unsupported glyphs | JUnit 5 |
| Unit — service | `DocumentStatus` transitions (every allowed and forbidden pair); `Backoff`; failure classification; cell formatters; profile validation; superseding rule | JUnit 5, Mockito |
| PDF assertions | Page count; title block on page 1 only; running header on pages 2+; "Page X of Y" on every page; table header text appears at the top of every continuation page; empty-category message present; bookmarks match categories | PDFBox `PDFTextStripper` per page |
| Integration — flow | API start → documents GENERATED → SENT on the embedded broker; message properties correct; skipped FIPS codes; unknown FIPS and unknown category give DATA_ERROR | `@SpringBootTest`, H2, embedded Artemis, Awaitility |
| Integration — concurrency | Two pollers on two application contexts against one H2 file database never claim the same document; second run for a state returns 409 | Two contexts in one test, Awaitility |
| Integration — failure | Broker down → SEND_FAILED with backoff → recovers when broker returns; expired lease recovered; manual retransmit; purge sets `CONTENT_PURGED_AT` and download returns 410 | Embedded Artemis stopped and restarted; `Clock` and `DbClock` test doubles |
| Compliance | PDF/UA-1 and PDF/A-2a on three sample reports | veraPDF (Phase 5 onward) |
| Performance | 100,000 records in one FIPS code and spread across 133 FIPS codes; record duration, peak heap, temp disk | Separate Maven profile `perf`, run before release |
| DB2 compatibility | Flyway migrations and all queries on real DB2 | Testcontainers with IBM's DB2 community image, Maven profile `db2-it`, nightly |

**Rules.** Tests never sleep fixed times; they use Awaitility. Test data uses fake names only. Each acceptance criterion in section 21 maps to at least one named test.

## 21. Implementation phases and acceptance criteria

Build in seven phases, one Claude Code session each. Phase 0 proves the riskiest integration first; every later phase keeps the application runnable end to end.

- **Phase 0 — Skeleton and SIB spike.** Maven multi-module build with quality plugins; Spring Boot WAR booting on H2 with Flyway V1–V4 and VA seed data; `DevDataSeeder`; a throwaway `SibSpikeRunner` (profile `sib-spike`) that sends one 25 MB bytes message with all section 13 properties.
  - `mvn verify` passes; the WAR deploys to Tomcat 9 and `/actuator/health` is UP.
  - The spike message reaches the real SIB queue and its properties are readable downstream; the required IBM jars are listed in `docs/DECISIONS.md`.
- **Phase 1 — PDF core.** All of report-pdf-core except `PdfUaTaggingContext`: layout, tables, wrapping, sanitizing, title block, summary reservation, decorator pass, bookmarks.
  - A demo test renders a 3-category report with 2,000 rows and one empty category; all PDF assertions in section 20 pass.
  - Layout unit tests cover every rule in section 10 pass 1.
- **Phase 2 — Data and rendering.** `JdbcReportDataProvider` with streaming, `ReportRowMapper`, profiles and `ProfileValidator`, `CellFormatters`, `SummaryCollector`, `FipsReportComposer`, `DocumentSpecFactory`.
  - A service test generates a real PDF for one seeded FIPS code; summary counts equal the rows rendered.
  - Unknown category produces `ReportDataException`; bad profile config stops startup.
- **Phase 3 — Orchestration and API.** Tracking entities and repositories, `DbClock`, claim protocol, `ReportRunService`, `GenerationPoller` and `GenerationWorker`, `RunFinalizer`, `LeaseRecoverySweeper`, `DailyReportScheduler` with ShedLock, all section 14 endpoints except retransmit.
  - API run reaches DISPATCHING with every document GENERATED; FIPS codes with no data are skipped; second concurrent run returns 409.
  - Two-context concurrency test never double-claims; an expired lease is recovered.
- **Phase 4 — Transmission.** `JmsReportTransmitter`, SIB and Artemis configs, `SendPoller` and `SendWorker`, failure classification, backoff, superseding, retransmit endpoints, `ContentPurgeJob`.
  - Full flow ends in SENT with correct properties on embedded Artemis.
  - Broker outage leads to SEND_FAILED then SENT after recovery; superseded documents are never sent; purge and 410 behave as specified.
- **Phase 5 — Accessibility.** `PdfUaTaggingContext`, XMP metadata, output intent, artifacts; turn on `reporting.tagging-enabled`.
  - veraPDF reports zero PDF/UA-1 and PDF/A-2a failures on the three sample reports.
  - Reading order in the structure tree matches section 12.
- **Phase 6 — Hardening.** Metrics, health, MDC logging, `setenv.sh`, `docs/RUNBOOK.md`, DB2 profile and DBA hand-off notes, `perf` profile.
  - The performance test meets NFR-03 or records the measured result and agreed target in `docs/DECISIONS.md`.
  - The `db2-it` profile passes against the DB2 community image.

**Dependencies outside the team** (start in Phase 0): SIB administrator (bus name, endpoints, queue, credentials, size check); downstream team (message contract sign-off, de-duplication on `reportDocId`); DBA (LOB tablespace, indexes on the real source table); internal Maven repository (IBM thin-client jars); accessibility reviewer (manual screen-reader check before go-live).

## 22. Assumptions, defaults and out of scope

The items below are assumptions or adopted defaults; change them here first, then in code.

**Assumptions to replace later**

- The source table `RPT_RECORD` and its columns are placeholders. Integrating the real table means changing `sql/report-rows.sql`, `sql/fips-workload.sql`, `ReportRowMapper`, and the column profile only.
- Categories `CAT1` to `CAT4` are placeholders and come from a column on the record, not from business rules.
- The logo is a placeholder image with placeholder alt text.
- Title, subtitle, and disclaimer texts are placeholders.

**Defaults adopted without explicit confirmation**

- Superseding (FR-15): an older unsent document is never sent once a newer one for the same FIPS code is GENERATED, SENDING, or SENT.
- Ad-hoc API runs are allowed on the same day as the scheduled run, but never while another run for the state is in its generation phase.
- The performance target in NFR-03 is a proposal until the Phase 6 test measures it.

**Out of scope**

- Authentication and authorization (to be added with Spring Security; `SecurityPlaceholderConfig` is the single place to add it).
- Purging or archiving the source data (handled by another mechanism).
- Downstream acknowledgements: SENT means the broker accepted the message.
- States other than Virginia (supported by design; added through data and configuration).
- A user interface beyond the REST API and the PDF itself.
