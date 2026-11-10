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
