package com.example.reporting.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reporting.TrackingRows;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * Phase 3 acceptance through the REST API on H2 (SPEC section 21): a run reaches DISPATCHING with every
 * document GENERATED, FIPS codes without data are skipped, a second concurrent run returns 409; plus the other
 * section 14 endpoints and their errors.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"h2", "test"})
@TestConstructor(autowireMode = AutowireMode.ALL)
class ReportRunApiIT {

    private static final String RUNS = "/api/v1/report-runs";

    private final TestRestTemplate http;
    private final JdbcTemplate jdbc;
    private final TrackingRows rows;

    ReportRunApiIT(TestRestTemplate http, JdbcTemplate jdbc) {
        this.http = http;
        this.jdbc = jdbc;
        this.rows = new TrackingRows(jdbc);
    }

    @BeforeEach
    void noActiveRun() {
        rows.awaitVirginiaLockFree();
    }

    @Test
    void fullRunReachesDispatchingWithEveryDocumentGeneratedAndASecondRunIsRejected() {
        // A record whose FIPS code is not in RPT_FIPS becomes a DATA_ERROR document at run creation.
        jdbc.update("INSERT INTO RPT_RECORD (STATE_CODE, FIPS_CODE, CATEGORY_CODE, CASE_NUMBER) "
                + "VALUES ('VA', '51999', 'CAT1', 'VA-88888888')");
        try {
            int fipsWithData = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT FIPS_CODE) FROM RPT_RECORD WHERE STATE_CODE = 'VA'", Integer.class);

            ResponseEntity<JsonNode> started = start("{\"stateCode\":\"VA\"}", "jdoe");
            ResponseEntity<JsonNode> second = start("{\"stateCode\":\"VA\"}", "someone-else");

            assertThat(started.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            long runId = started.getBody().get("runId").asLong();
            assertThat(started.getHeaders().getLocation()).hasToString(RUNS + "/" + runId);
            assertThat(started.getBody().get("status").asText()).isEqualTo("RUNNING");
            assertThat(started.getBody().get("triggerType").asText()).isEqualTo("API");
            assertThat(started.getBody().get("totalDocuments").asInt()).isEqualTo(fipsWithData);
            assertThat(started.getBody().get("dataAsOf").asText()).matches(
                    "\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d-0[45]:00");

            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(second.getHeaders().getContentType().toString()).startsWith("application/problem+json");
            assertThat(second.getBody().get("type").asText()).endsWith("/run-already-active");
            assertThat(second.getBody().get("detail").asText())
                    .isEqualTo("State VA already has run " + runId + " in progress.");
            assertThat(second.getBody().get("instance").asText()).isEqualTo(RUNS);

            rows.awaitRunStatus(runId, "DISPATCHING");
            JsonNode detail = http.getForObject(RUNS + "/" + runId, JsonNode.class);
            assertThat(detail.get("requestedBy").asText()).isEqualTo("jdoe");
            assertThat(detail.get("completedAt").isNull()).isTrue();
            assertThat(detail.get("documentsByStatus").get("GENERATED").asInt()).isEqualTo(fipsWithData - 1);
            assertThat(detail.get("documentsByStatus").get("DATA_ERROR").asInt()).isEqualTo(1);
            assertThat(detail.get("documentsByStatus")).hasSize(2);

            List<Map<String, Object>> documents = rows.documentsOf(runId);
            assertThat(documents).hasSize(fipsWithData)
                    .allSatisfy(d -> assertThat(((Number) d.get("GENERATION_ATTEMPTS")).intValue())
                            .isEqualTo(((String) d.get("STATUS")).trim().equals("DATA_ERROR") ? 0 : 1));
            Map<String, Object> unknown = documents.stream()
                    .filter(d -> ((String) d.get("FIPS_CODE")).trim().equals("51999")).findFirst().orElseThrow();
            assertThat(unknown.get("STATUS")).asString().isEqualTo("DATA_ERROR");
            assertThat(unknown.get("LAST_ERROR")).isEqualTo("FIPS code not in RPT_FIPS for state");
            long rendered = documents.stream().filter(d -> d.get("RECORD_COUNT") != null)
                    .mapToLong(d -> ((Number) d.get("RECORD_COUNT")).longValue()).sum();
            assertThat(rendered).isEqualTo(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM RPT_RECORD WHERE STATE_CODE = 'VA' AND FIPS_CODE <> '51999'", Long.class));
        } finally {
            jdbc.update("DELETE FROM RPT_RECORD WHERE FIPS_CODE = '51999'");
        }
    }

    @Test
    void fipsFilterSkipsCodesWithoutRecordsAndAnEmptyRunCompletesAtOnce() {
        ResponseEntity<JsonNode> empty = start("{\"stateCode\":\"VA\",\"fipsCodes\":[\"51005\"]}", null);
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(empty.getBody().get("totalDocuments").asInt()).isZero();
        assertThat(empty.getBody().get("status").asText()).isEqualTo("COMPLETED");
        assertThat(empty.getBody().get("requestedBy").asText()).isEqualTo("anonymous");

        ResponseEntity<JsonNode> filtered = start("{\"stateCode\":\"VA\",\"fipsCodes\":[\"51013\",\"51005\"]}", "j");
        long runId = filtered.getBody().get("runId").asLong();
        assertThat(filtered.getBody().get("totalDocuments").asInt()).isEqualTo(1);
        rows.awaitRunStatus(runId, "DISPATCHING");

        JsonNode page = http.getForObject(RUNS + "/" + runId + "/documents?status=GENERATED", JsonNode.class);
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        JsonNode document = page.get("content").get(0);
        assertThat(document.get("fipsCode").asText()).isEqualTo("51013");
        assertThat(document.get("localityName").asText()).isEqualTo("Arlington County");
        assertThat(document.get("contentAvailable").asBoolean()).isTrue();
        assertThat(http.getForObject(RUNS + "/" + runId + "/documents?status=SENT", JsonNode.class)
                .get("totalElements").asInt()).isZero();
    }

    @Test
    void unknownCategoryEndsTheRunCompletedWithErrors() {
        jdbc.update("INSERT INTO RPT_RECORD (STATE_CODE, FIPS_CODE, CATEGORY_CODE, CASE_NUMBER) "
                + "VALUES ('VA', '51001', 'CATX', 'VA-77777777')");
        try {
            long runId = start("{\"stateCode\":\"VA\",\"fipsCodes\":[\"51001\"]}", "j").getBody()
                    .get("runId").asLong();
            rows.awaitRunStatus(runId, "COMPLETED_WITH_ERRORS");
            JsonNode run = http.getForObject(RUNS + "/" + runId, JsonNode.class);
            assertThat(run.get("completedAt").isNull()).isFalse();
            JsonNode document = http.getForObject(RUNS + "/" + runId + "/documents", JsonNode.class)
                    .get("content").get(0);
            assertThat(document.get("status").asText()).isEqualTo("DATA_ERROR");
            assertThat(document.get("lastError").asText()).contains("CATX").doesNotContain("VA-77777777");
            assertThat(document.get("contentAvailable").asBoolean()).isFalse();
            ResponseEntity<JsonNode> content = http.getForEntity("/api/v1/report-documents/"
                    + document.get("docId").asLong() + "/content", JsonNode.class);
            assertThat(content.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        } finally {
            jdbc.update("DELETE FROM RPT_RECORD WHERE FIPS_CODE = '51001'");
        }
    }

    @Test
    void documentDetailAndDownloadAndPurgedContent() throws Exception {
        long runId = start("{\"stateCode\":\"VA\",\"fipsCodes\":[\"51013\"]}", "j").getBody().get("runId").asLong();
        rows.awaitRunStatus(runId, "DISPATCHING");
        long docId = rows.documentsOf(runId).stream().map(d -> ((Number) d.get("DOC_ID")).longValue())
                .findFirst().orElseThrow();

        JsonNode detail = http.getForObject("/api/v1/report-documents/" + docId, JsonNode.class);
        assertThat(detail.get("status").asText()).isEqualTo("GENERATED");
        assertThat(detail.get("pageCount").asInt()).isPositive();
        assertThat(detail.get("generationAttempts").asInt()).isEqualTo(1);
        assertThat(detail.get("sendAttempts").asInt()).isZero();
        assertThat(detail.get("generatedAt").asText()).isNotEmpty();
        assertThat(detail.get("contentAvailable").asBoolean()).isTrue();

        ResponseEntity<byte[]> pdf = http.getForEntity("/api/v1/report-documents/" + docId + "/content",
                byte[].class);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(pdf.getHeaders().getContentDisposition().getFilename())
                .matches("VA_51013_\\d{8}T\\d{4}_" + docId + "\\.pdf");
        assertThat(new String(pdf.getBody(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.getBody()).hasSize(detail.get("sizeBytes").asInt());
        assertThat(hex(MessageDigest.getInstance("SHA-256").digest(pdf.getBody())))
                .isEqualTo(detail.get("sha256").asText());

        jdbc.update("UPDATE REPORT_DOCUMENT SET CONTENT_PURGED_AT = CURRENT_TIMESTAMP WHERE DOC_ID = ?", docId);
        ResponseEntity<JsonNode> gone = http.getForEntity("/api/v1/report-documents/" + docId + "/content",
                JsonNode.class);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(gone.getBody().get("type").asText()).endsWith("/content-purged");
        assertThat(http.getForObject("/api/v1/report-documents/" + docId, JsonNode.class)
                .get("contentAvailable").asBoolean()).isFalse();
    }

    @Test
    void listsRunsNewestFirstWithPaging() {
        long first = start("{\"stateCode\":\"VA\",\"fipsCodes\":[\"51005\"]}", "a").getBody().get("runId").asLong();
        long second = start("{\"stateCode\":\"VA\",\"fipsCodes\":[\"51005\"]}", "b").getBody().get("runId").asLong();

        JsonNode page = http.getForObject(RUNS + "?stateCode=VA&page=0&size=2", JsonNode.class);
        assertThat(page.get("size").asInt()).isEqualTo(2);
        assertThat(page.get("content")).hasSize(2);
        assertThat(page.get("content").get(0).get("runId").asLong()).isEqualTo(second);
        assertThat(page.get("content").get(1).get("runId").asLong()).isEqualTo(first);
        assertThat(page.get("content").get(0).has("documentsByStatus")).isFalse();
        assertThat(http.getForObject(RUNS + "?stateCode=MD", JsonNode.class).get("totalElements").asInt()).isZero();
        assertThat(http.getForObject(RUNS, JsonNode.class).get("totalElements").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void errorsUseOneProblemShape() {
        assertProblem(start("{\"stateCode\":\"va\"}", null), HttpStatus.BAD_REQUEST, "invalid-request",
                "stateCode must be a two-letter upper-case state code");
        assertProblem(start("{\"stateCode\":\"VA\",\"fipsCodes\":[\"5101\"]}", null), HttpStatus.BAD_REQUEST,
                "invalid-request", "must have five digits");
        assertProblem(start("{\"stateCode\":\"VA\",\"fipsCodes\":[\"24001\"]}", null), HttpStatus.BAD_REQUEST,
                "invalid-request", "FIPS codes not active for state VA: [24001]");
        assertProblem(start("{\"stateCode\":\"ZZ\"}", null), HttpStatus.NOT_FOUND, "unknown-state", "ZZ");
        assertProblem(start("not json", null), HttpStatus.BAD_REQUEST, "invalid-request", "not valid JSON");
        assertProblem(http.getForEntity(RUNS + "/999999", JsonNode.class), HttpStatus.NOT_FOUND, "not-found",
                "Run 999999 does not exist.");
        assertProblem(http.getForEntity(RUNS + "/999999/documents", JsonNode.class), HttpStatus.NOT_FOUND,
                "not-found", "Run 999999");
        assertProblem(http.getForEntity(RUNS + "/1/documents?status=BOGUS", JsonNode.class),
                HttpStatus.BAD_REQUEST, "invalid-request", "parameter status");
        assertProblem(http.getForEntity(RUNS + "?size=201", JsonNode.class), HttpStatus.BAD_REQUEST,
                "invalid-request", "size must be less than or equal to 200");
        assertProblem(http.getForEntity(RUNS + "?stateCode=virginia", JsonNode.class), HttpStatus.BAD_REQUEST,
                "invalid-request", "stateCode");
        assertProblem(http.getForEntity("/api/v1/report-documents/999999", JsonNode.class), HttpStatus.NOT_FOUND,
                "not-found", "Document 999999");
        assertProblem(http.getForEntity("/api/v1/report-documents/999999/content", JsonNode.class),
                HttpStatus.NOT_FOUND, "not-found", "Document 999999");
        assertProblem(http.exchange(RUNS + "/1", HttpMethod.DELETE, null, JsonNode.class),
                HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "DELETE");
        HttpHeaders text = new HttpHeaders();
        text.setContentType(MediaType.TEXT_PLAIN);
        assertProblem(http.postForEntity(RUNS, new HttpEntity<>("VA", text), JsonNode.class),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "text/plain");
    }

    private ResponseEntity<JsonNode> start(String json, String requestedBy) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (requestedBy != null) {
            headers.set(ReportRunController.REQUESTED_BY, requestedBy);
        }
        return http.postForEntity(RUNS, new HttpEntity<>(json, headers), JsonNode.class);
    }

    private static void assertProblem(ResponseEntity<JsonNode> response, HttpStatus status, String slug,
            String detailPart) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        JsonNode body = response.getBody();
        assertThat(body.get("type").asText()).isEqualTo("https://example.com/problems/" + slug);
        assertThat(body.get("status").asInt()).isEqualTo(status.value());
        assertThat(body.get("title").asText()).isNotBlank();
        assertThat(body.get("detail").asText()).contains(detailPart);
        assertThat(body.get("instance").asText()).startsWith("/api/v1/");
    }

    private static String hex(byte[] bytes) {
        return IntStream.range(0, bytes.length)
                .mapToObj(i -> String.format("%02x", bytes[i])).collect(Collectors.joining());
    }
}
