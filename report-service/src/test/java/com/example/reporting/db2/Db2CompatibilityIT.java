package com.example.reporting.db2;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reporting.ReportServiceApplication;
import com.example.reporting.TestRecords;
import com.example.reporting.TrackingRows;
import com.example.reporting.orchestration.ReportRunService;
import com.example.reporting.orchestration.StartRunCommand;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.TriggerType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.Db2Container;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * DB2 compatibility (SPEC section 20, profile {@code db2-it}): the service starts on IBM's DB2 community image
 * with the {@code db2} profile, Flyway applies the common migrations, and a run is generated and sent using the
 * same SQL as in production (claims, workload, report rows, ShedLock with database time).
 */
@Tag("db2")
@Testcontainers
class Db2CompatibilityIT {

    @Container
    private static final Db2Container DB2 = new Db2Container(DockerImageName.parse("icr.io/db2_community/db2:11.5.9.0")
            .asCompatibleSubstituteFor("ibmcom/db2")).acceptLicense();

    @Test
    void aRunCompletesOnDb2() throws IOException {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ReportServiceApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("db2", "test")
                .run("--spring.datasource.url=" + DB2.getJdbcUrl(),
                        "--spring.datasource.username=" + DB2.getUsername(),
                        "--spring.datasource.password=" + DB2.getPassword(),
                        "--spring.artemis.mode=embedded",
                        "--spring.artemis.embedded.server-id=3001",
                        "--reporting.generation.temp-dir=target/fips-reports-db2")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForList("SELECT \"version\" FROM \"flyway_schema_history\" "
                    + "WHERE \"version\" IS NOT NULL ORDER BY \"installed_rank\"", String.class))
                    .containsExactly("1", "3", "4");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM RPT_FIPS WHERE STATE_CODE = 'VA'", Integer.class))
                    .isEqualTo(133);

            createSourceTable(jdbc);
            TestRecords.insert(jdbc, List.of("51059", "51013", "51107"), 300);
            ReportRun run = context.getBean(ReportRunService.class).startRun(StartRunCommand.builder()
                    .stateCode("VA").triggerType(TriggerType.API).requestedBy("db2-it").build());
            TrackingRows rows = new TrackingRows(jdbc);
            rows.awaitRunStatus(run.getRunId(), "COMPLETED");

            assertThat(rows.documentsOf(run.getRunId())).hasSize(3)
                    .allSatisfy(d -> assertThat(((String) d.get("STATUS")).trim()).isEqualTo("SENT"));
            assertThat(jdbc.queryForList("SELECT NAME FROM SHEDLOCK", String.class)).contains("run-finalizer");
            Map<String, Object> content = jdbc.queryForMap("SELECT COUNT(*) AS N FROM REPORT_CONTENT");
            assertThat(((Number) content.get("N")).intValue()).isEqualTo(3);
        }
    }

    /** The dev source table lives in the h2 migrations only; DB2 gets the same DDL for this test. */
    private static void createSourceTable(JdbcTemplate jdbc) throws IOException {
        try (InputStream in = new ClassPathResource("db/migration/h2/V2__dev_source_records.sql").getInputStream()) {
            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String statement : script.split(";")) {
                String sql = statement.lines().filter(line -> !line.trim().startsWith("--"))
                        .reduce("", (a, b) -> a + "\n" + b).trim();
                if (!sql.isEmpty()) {
                    jdbc.execute(sql);
                }
            }
        }
    }
}
