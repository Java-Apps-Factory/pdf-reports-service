package com.example.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.util.StreamUtils;

/**
 * Phase 0 acceptance: the application boots on H2 (DB2 mode) with Flyway V1-V4, the Virginia
 * reference seed and the development data, and reports healthy.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"h2", "test"})
@TestConstructor(autowireMode = AutowireMode.ALL)
class ReportServiceApplicationIT {

    private final JdbcTemplate jdbcTemplate;
    private final TestRestTemplate restTemplate;

    ReportServiceApplicationIT(JdbcTemplate jdbcTemplate, TestRestTemplate restTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = restTemplate;
    }

    @Test
    void healthEndpointIsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void flywayAppliedAllVersionedMigrationsAndTheSeed() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"version\" IS NOT NULL "
                        + "AND \"success\" = TRUE ORDER BY \"installed_rank\"", String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4");
        for (String table : List.of("REPORT_RUN", "REPORT_DOCUMENT", "REPORT_CONTENT", "SHEDLOCK")) {
            assertThat(count("SELECT COUNT(*) FROM " + table)).as(table).isZero();
        }
    }

    @Test
    void virginiaReferenceDataIsSeeded() {
        assertThat(count("SELECT COUNT(*) FROM RPT_STATE WHERE STATE_CODE = 'VA' AND ACTIVE = 1")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM RPT_FIPS WHERE STATE_CODE = 'VA' AND ACTIVE = 1")).isEqualTo(133);
        assertThat(count("SELECT COUNT(*) FROM RPT_FIPS WHERE FIPS_CODE LIKE '51%' AND LENGTH(FIPS_CODE) = 5"))
                .isEqualTo(133);
        assertThat(count("SELECT COUNT(*) FROM RPT_FIPS WHERE LOCALITY_NAME LIKE '% County'")).isEqualTo(95);
        assertThat(count("SELECT COUNT(*) FROM RPT_FIPS WHERE LOCALITY_NAME LIKE '% city'")).isEqualTo(38);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT LOCALITY_NAME FROM RPT_FIPS WHERE FIPS_CODE = '51059'", String.class))
                .isEqualTo("Fairfax County");
        assertThat(jdbcTemplate.queryForList(
                "SELECT CATEGORY_CODE FROM RPT_CATEGORY WHERE STATE_CODE = 'VA' ORDER BY DISPLAY_ORDER",
                String.class)).containsExactly("CAT1", "CAT2", "CAT3", "CAT4");
    }

    @Test
    void devDataIsSeededWithAnEmptyCategoryAndALargeFipsCode() {
        assertThat(count("SELECT COUNT(*) FROM RPT_RECORD")).isEqualTo(600);
        assertThat(count("SELECT COUNT(*) FROM RPT_RECORD WHERE FIPS_CODE = '51013'")).isPositive();
        assertThat(count("SELECT COUNT(*) FROM RPT_RECORD WHERE FIPS_CODE = '51013' AND CATEGORY_CODE = 'CAT3'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM RPT_RECORD WHERE FIPS_CODE = '51059'")).isGreaterThan(150);
        assertThat(count("SELECT COUNT(*) FROM RPT_RECORD r WHERE NOT EXISTS "
                + "(SELECT 1 FROM RPT_FIPS f WHERE f.FIPS_CODE = r.FIPS_CODE)")).isZero();
    }

    @Test
    void reportQueriesRunOnH2InDb2Mode() throws IOException {
        List<Map<String, Object>> workload = jdbcTemplate.queryForList(sql("sql/fips-workload.sql"), "VA");
        long total = workload.stream().mapToLong(row -> ((Number) row.get("RECORD_COUNT")).longValue()).sum();

        assertThat(workload).hasSizeBetween(15, 20);
        assertThat(total).isEqualTo(600);
        assertThat(workload).allMatch(row -> ((Number) row.get("UNKNOWN_FIPS")).intValue() == 0);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql("sql/report-rows.sql"), "VA", "51013");

        assertThat(rows).isNotEmpty();
        assertThat(rows).extracting(row -> ((Number) row.get("DISPLAY_ORDER")).intValue()).isSorted();
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private static String sql(String path) throws IOException {
        return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
    }
}
