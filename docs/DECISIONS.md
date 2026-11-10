# Decisions

Decisions the spec does not cover, per CLAUDE.md ("When the spec is silent"). Newest phase last.

## Phase 0 — Skeleton and SIB spike

| Date | Decision | Reason |
|---|---|---|
| 2026-11-10 | `report-parent` inherits from `spring-boot-starter-parent` 2.7.18. | Simplest way to get Boot dependency and plugin management for both modules (SPEC section 5). |
| 2026-11-10 | The build requires JDK 11–17 (Maven Enforcer); compile target stays `release=11`. Build and CI use JDK 11. | Boot 2.7's test stack (Hibernate 5.6, ByteBuddy, JaCoCo 0.8.12, SpotBugs 4.8) does not support JDK 21+; production is Java 11. |
| 2026-11-10 | Pinned versions: PDFBox 3.0.5, ShedLock 4.48.0, DB2 JCC 11.5.9.0, Checkstyle 10.17.0 (plugin 3.4.0), SpotBugs plugin 4.8.6.6, JaCoCo 0.8.12. | Newest lines that still run on Java 11 at the time of writing. Re-check at each phase. |
| 2026-11-10 | The JMS API comes from Boot-managed `jakarta.jms:jakarta.jms-api` 2.0.3 (transitive via `spring-boot-starter-artemis`), not `javax.jms:javax.jms-api` 2.0.1. | Both jars contain the same `javax.jms.*` classes; adding the second would put duplicates on the classpath. Code still uses JMS 1.1 calls only. |
| 2026-11-10 | `spring.datasource.hikari.leak-detection-threshold` is `1200000` (ms), not `20m` as printed in SPEC section 16. | Hikari binds this property as a `long` in milliseconds; `20m` fails startup. Same value, different notation. **SPEC.md should be corrected.** |
| 2026-11-10 | `ReportingProperties` is constructor-bound and immutable, and binds only what each phase uses (Phase 0: `node-id`, `business-zone`, `db.now-query`, `transmission.queue-name`, `dev.seed-records`). The full YAML from section 16 is already in `application.yml`. | Immutable config matches the coding standards; unbound keys are ignored by Boot, so later phases add their groups together with the code that reads them. |
| 2026-11-10 | `DevDataSeeder` lives in `com.example.reporting.dev`, seeds only when `RPT_RECORD` is empty, and uses a fixed random seed. | The spec names the class but no package; idempotent and deterministic seeding keeps restarts and tests repeatable. |
| 2026-11-10 | `R__seed_va_reference.sql` uses standard `MERGE ... USING (VALUES ...)`. | A repeatable migration must be re-runnable; this form runs unchanged on H2 (MODE=DB2) and is valid DB2 LUW syntax (to be confirmed by the `db2-it` profile in Phase 6). |
| 2026-11-10 | Virginia seed = 95 counties + 38 independent cities = 133 codes, named in the Census style ("Fairfax County", "Fairfax city"). Bedford city (51515) is excluded: it merged into Bedford County in 2013. | Matches the current Census county-equivalent list. **Open:** written from the published list but not machine-diffed against a Census download; do that diff before go-live. |
| 2026-11-10 | The SIB spike (`spike` package) calls the IBM thin client reflectively; the jar is added only by the Maven profile `-Psib` with placeholder coordinates `com.ibm.ws:com.ibm.ws.sib.client.thin.jms:9.0.0`. | The jar is not on Maven Central, so the normal build must not need it. Adjust the coordinates to whatever the internal repository uses. Phase 4 can compile against the jar directly once it is published. |
| 2026-11-10 | The spike sender is tested against embedded Artemis with a 1 MB body (`SibSpikeSenderIT`); spike payload size is `reporting.sib.spike-payload-bytes` (default 25 MB). | Proves the JMS 1.1 call sequence and the message contract without SIB; the 25 MB size check is only meaningful on the real bus. |
| 2026-11-10 | Integration tests are `*IT` classes run by Failsafe, on in-memory H2 (`application-test.yml`). | Keeps the developer's file database (`./data/reportdb`) out of test runs. |
| 2026-11-10 | Checkstyle also bans `jakarta.*`/`lombok.*` imports and `@Autowired`/`@Value` field injection. SpotBugs excludes `EI_EXPOSE_REP2` globally. | Enforces CLAUDE.md rules mechanically. Spring beans intentionally store injected shared collaborators; the exclusion is justified in `config/spotbugs/exclude.xml`. |
| 2026-11-10 | Noto Sans Regular, Bold and Italic are the static hinted TTFs from the Noto project (`notofonts/notofonts.github.io`, `fonts/NotoSans/hinted/ttf`), with the SIL OFL 1.1 text in `fonts/OFL.txt`. SHA-256: Regular `478c558e…3823`, Bold `1df075a3…a913`, Italic `467e3f89…ecb5`. The sRGB ICC profile is still to be added (Phase 5). | Official upstream source; static (not variable) TTFs load with `PDType0Font.load` in PDFBox 3.0.5 (checked). The OFL requires the licence to travel with the fonts. |
| 2026-11-10 | `sql/fips-workload.sql` and `sql/report-rows.sql` are checked in now and executed by `ReportServiceApplicationIT`. | Early proof that `FOR READ ONLY` and the joins run on H2 in DB2 mode (they do). |

### Phase 0 acceptance status

| Criterion | Status |
|---|---|
| `mvn verify` passes | **Done** on JDK 11 (4 unit + 8 integration tests, Checkstyle, SpotBugs, JaCoCo). |
| WAR boots on H2 with Flyway V1–V4, VA seed, DevDataSeeder; `/actuator/health` is UP | **Done** with the embedded container (`java -jar report-service.war`, profile `h2`). |
| WAR deploys to external Tomcat 9 | **Not verified** — no Tomcat 9 on the build machine. |
| Spike message reaches the real SIB queue; properties readable downstream | **Not run** — needs the thin-client jar, bus name, endpoints, queue and credentials from the SIB administrator. |
| Required IBM jars listed here | **Open** — fill in after the spike runs on the target JDK (expected: `com.ibm.ws.sib.client.thin.jms_9.0.0.jar`; possibly `com.ibm.ws.orb_9.0.jar` on a non-IBM JDK). |

### How to run the SIB spike

1. Publish the thin-client jar to the internal Maven repository (adjust `sib-thin-client.*` in the parent POM).
2. `mvn -Psib -DskipTests package`
3. Set `SIB_BUS`, `SIB_ENDPOINTS`, optionally `SIB_USER` / `SIB_PASSWORD`, and deploy `report-service.war` to Tomcat 9 with
   `-Dspring.profiles.active=h2,sib,sib-spike`.
4. The log shows `SIB spike: broker accepted the message, JMSMessageID=...`. Ask the downstream team to confirm the
   eleven properties of section 13, then record the result and the jar list above.

## Phase 1 — PDF core

| Date | Decision | Reason |
|---|---|---|
| 2026-11-10 | The supported API is the root package types plus `TableSpec`, `ColumnSpec`, `Alignment`, `CellValue`, `FontSet`, `PageGeometry`, `Margins` and `TaggingContext`/`NoOpTaggingContext`. Helper classes in `layout`, `table`, `text`, `decorate` and `outline` are `public` but documented "Internal to report-pdf-core"; `StreamingReportDocumentWriter` and `PdfFontMeasurer` are package-private. | SPEC section 10 wants everything except the API package-private, but section 6 puts the helpers in sub-packages, and Java has no cross-package private visibility. A JPMS module would complicate the WAR. |
| 2026-11-10 | `ReportDocumentWriter.beginSection/addRow/addEmptyMessage/endSection` declare `throws IOException`. | They write page content to PDFBox temp files, which can fail; the section 10 sketch omitted the clause. |
| 2026-11-10 | `SectionSpec.heading` is a template: the token `{n}` is replaced by the section's row count (US grouping, e.g. `1,200`) at `endSection`. Section headings are therefore drawn in pass 2, like the summary. The resolved heading is the bookmark title. | The heading "… ({n} records)" (section 11) needs the count before the rows are drawn, but the count is only known once the stream has passed (AD-05). Wording such as "1 records" vs "1 record" is the caller's choice of template. |
| 2026-11-10 | The title block is drawn in pass 2 together with the other page decorations. `DocumentSpec` carries `title`, `subtitle`, `fipsLine` and a list of 9 pt `infoLines`; the service formats the "Data as of …" and "Report ID …" lines. `reportId` is the footer centre text. | Keeps all formatting in the service (section 10: "the core never knows about dates"). |
| 2026-11-10 | Font sizes, spacing and fills are constants in `layout.LayoutMetrics`; `DocumentSpec` holds only `PageGeometry` (page size, margins, title block 96 pt, running header 28 pt, footer 28 pt). | Section 11 says there is one template; making every size configurable would add knobs no state uses. Geometry is still configurable as section 10 lists it. |
| 2026-11-10 | Summary table: Category 260 pt and Records 100 pt wide, left-aligned on the page; rows are one line (label cut with an ellipsis); counts use US grouping. The summary heading is also the first bookmark. `finish` rejects more summary rows than `summaryRowCount` reserved. | Section 11 does not give summary widths; a compact table reads better than a full-width one with two columns. |
| 2026-11-10 | Page 1 must keep at least 72 pt of body below the reserved summary, otherwise `open` throws `IllegalArgumentException`. | Guarantees room for at least one section heading, header row and data row on page 1. |
| 2026-11-10 | The 12 pt space above a section heading is omitted at the top of a page. The summary area counts as content, so the first heading on page 1 keeps its space. | Avoids a gap under the running header without crowding the summary. |
| 2026-11-10 | A row that does not fit moves to a continuation page only if the current page already holds rows of this section (or less than one row height is left). Otherwise — directly under a section start or a repeated header — it is cut to the remaining space with an ellipsis and a warning naming the row index and `DocumentSpec.logContext` (the FIPS code). | Moving such a row could never give it more room; this is the "row taller than a full body area" rule applied where it actually occurs. |
| 2026-11-10 | `GlyphSanitizer` turns tab, CR and LF into spaces; all other control characters, Unicode format characters (e.g. zero-width space) and characters the font cannot encode become `?`. | Line breaks in a description should read as spaces, not as `?`; everything else follows section 10. |
| 2026-11-10 | The empty-category message is one line (cut with an ellipsis if wider than the page) and allowed only in a section without rows. | Section 11 styles it as a single 9 pt italic line. |
| 2026-11-10 | Footer zones: disclaimer on the left in (half the content width − 90 pt), wrapped to at most two lines; report ID centred in a 180 pt zone; "Page X of Y" right-aligned. Running header: right text up to half the width, left text cut to the rest. | Section 11 names the contents but not the zone widths. |
| 2026-11-10 | `TaggingContext` in Phase 1: `beginStructure(Role)`, `beginFigure(altText)`, `endStructure()`, `markContent(stream, page, drawing)`, `beginArtifact(stream, type)`, `endArtifact(stream)`. Layout code already calls them (TH/TD in TR, artifacts for fills, borders, rules, continuation labels, repeated header rows, headers and footers). Phase 5 may extend the interface (e.g. TH scope, attaching pass-2 content to pass-1 elements). | Implements AD-12 now so tagging does not leak into layout later; the exact needs of the PDF/UA structure tree are only known when Phase 5 builds it. |
| 2026-11-10 | `ReportDocumentWriters.open` throws `UnsupportedOperationException` when `DocumentSpec.taggingEnabled` is true, until Phase 5. | Fails loudly instead of silently producing untagged output when tagging was requested. |
| 2026-11-10 | Phase 1 already writes Info title/subject/author/creator, catalog `/Lang`, `DisplayDocTitle` and `PageMode /UseOutlines`. No creation date is written. | Cheap, required later by section 12; the core has no clock, and leaving the date out keeps output reproducible for tests. |
| 2026-11-10 | report-pdf-core logs with `java.lang.System.Logger` (JUL backend), never record content. | The core may import only the JDK and PDFBox; Spring Boot routes JUL to SLF4J. |
| 2026-11-10 | Core tests read the Noto Sans fonts from `../report-service/src/main/resources/fonts` and generate the logo in memory. | Avoids a second copy of 1.9 MB of fonts in git; Surefire runs with the module directory as working directory. |
| 2026-11-10 | The Phase 1 demo ("3-category report with 2,000 rows and one empty category") is three categories in total: Fees 1,200 rows, Inspections empty, Permits 800 rows (`DemoReportTest`, 96 pages). It writes `report-pdf-core/target/demo-report.pdf` for manual review. | Reading of the acceptance criterion that keeps the row total at exactly 2,000. |
| 2026-11-10 | SpotBugs: `EI_EXPOSE_REP` is excluded only for `FontFace.font()` and `Canvas.stream()` (justified in `config/spotbugs/exclude.xml`). | They hand out document-scoped PDFBox handles on purpose. |

### Phase 1 acceptance status

| Criterion | Status |
|---|---|
| Demo renders a 3-category report with 2,000 rows and one empty category; all section 20 PDF assertions pass | **Done** — `DemoReportTest`: page count, title block on page 1 only, running header on pages 2+, "Page X of Y" on every page, table header at the top of every continuation page, empty-category message, bookmarks match categories, summary counts, every row rendered once. |
| Layout unit tests cover every pass 1 rule of section 10 | **Done** — temp-file stream cache and cleanup, subset font embedding, summary reservation, body area (`PageGeometryTest`), row height from wrapping (`RowLayoutTest`), page breaks with continuation label and repeated header, oversized-row truncation, keep-together (`KeepTogetherPolicyTest` + writer test), no-wrap ellipsis, `GlyphSanitizer` (`StreamingReportDocumentWriterTest`, `TextWrapperTest`, `GlyphSanitizerTest`). |
| `mvn verify` passes | **Done** on JDK 11: 69 core unit tests, Checkstyle, SpotBugs, JaCoCo (core line coverage 98%). |
