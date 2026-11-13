package com.example.reporting.transmission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.reporting.TrackingRows;
import com.example.reporting.maintenance.ContentPurgeJob;
import com.example.reporting.tracking.DbClock;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * Phase 4 acceptance on embedded Artemis (SPEC section 21): the full flow ends in SENT with the contract
 * properties; a broker outage leads to SEND_FAILED and then SENT after recovery; superseded documents are never
 * sent; retransmit and purge behave as specified. Uses its own database, so no other test context's poller can
 * send these documents to a different broker.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:transmission;MODE=DB2;DATABASE_TO_UPPER=TRUE;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles({"h2", "test"})
@TestConstructor(autowireMode = AutowireMode.ALL)
class TransmissionFlowIT {

    private static final String QUEUE = "REPORT.OUT.QUEUE";
    private static final byte[] PDF = "%PDF-1.7 stored test content".getBytes(StandardCharsets.US_ASCII);

    private final TestRestTemplate http;
    private final JdbcTemplate jdbc;
    private final ConnectionFactory connectionFactory;
    private final EmbeddedActiveMQ broker;
    private final ContentPurgeJob purgeJob;
    private final DbClock clock;
    private final TrackingRows rows;

    TransmissionFlowIT(TestRestTemplate http, JdbcTemplate jdbc, ConnectionFactory connectionFactory,
            EmbeddedActiveMQ broker, ContentPurgeJob purgeJob, DbClock clock) {
        this.http = http;
        this.jdbc = jdbc;
        this.connectionFactory = connectionFactory;
        this.broker = broker;
        this.purgeJob = purgeJob;
        this.clock = clock;
        this.rows = new TrackingRows(jdbc);
    }

    @Test
    void fullFlowEndsInSentWithTheContractProperties() throws Exception {
        rows.awaitVirginiaLockFree();
        long runId = startRun("51013");
        rows.awaitRunStatus(runId, "COMPLETED");

        Map<String, Object> document = rows.documentsOf(runId).get(0);
        long docId = ((Number) document.get("DOC_ID")).longValue();
        Map<String, Object> row = rows.document(docId);
        assertThat(row.get("STATUS")).asString().isEqualTo("SENT");
        assertThat(row.get("SENT_AT")).isNotNull();
        assertThat(((Number) row.get("SEND_ATTEMPTS")).intValue()).isEqualTo(1);

        Message message = receive(docId);
        assertThat(message).isInstanceOf(BytesMessage.class);
        assertThat(message.getJMSMessageID()).isEqualTo(row.get("JMS_MESSAGE_ID"));
        assertThat(message.getJMSDeliveryMode()).isEqualTo(DeliveryMode.PERSISTENT);
        assertThat(message.getJMSExpiration()).isZero();
        assertThat(message.getLongProperty(ReportMessageProperties.RUN_ID)).isEqualTo(runId);
        assertThat(message.getStringProperty(ReportMessageProperties.STATE_CODE)).isEqualTo("VA");
        assertThat(message.getStringProperty(ReportMessageProperties.FIPS_CODE)).isEqualTo("51013");
        assertThat(message.getStringProperty(ReportMessageProperties.GENERATED_AT))
                .matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d-0[45]:00");
        JsonNode run = http.getForObject("/api/v1/report-runs/" + runId, JsonNode.class);
        assertThat(message.getStringProperty(ReportMessageProperties.DATA_AS_OF))
                .isEqualTo(run.get("dataAsOf").asText());
        assertThat(message.getIntProperty(ReportMessageProperties.RECORD_COUNT))
                .isEqualTo(((Number) row.get("RECORD_COUNT")).intValue());
        assertThat(message.getIntProperty(ReportMessageProperties.PAGE_COUNT))
                .isEqualTo(((Number) row.get("PAGE_COUNT")).intValue());
        assertThat(message.getStringProperty(ReportMessageProperties.CONTENT_TYPE)).isEqualTo("application/pdf");
        assertThat(message.getIntProperty(ReportMessageProperties.SCHEMA_VERSION)).isEqualTo(1);
        BytesMessage bytes = (BytesMessage) message;
        byte[] body = new byte[(int) bytes.getBodyLength()];
        bytes.readBytes(body);
        assertThat(new String(body, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(message.getStringProperty(ReportMessageProperties.SHA256)).isEqualTo(sha256(body))
                .isEqualTo(((String) row.get("SHA256")).trim());
        assertThat(Collections.list(message.getPropertyNames())).filteredOn(n -> !n.toString().startsWith("JMS"))
                .hasSize(11);
    }

    @Test
    void aBrokerOutageLeadsToSendFailedAndThenSentAfterRecovery() throws Exception {
        rows.awaitVirginiaLockFree();
        broker.stop();
        long docId;
        try {
            long runId = startRun("51003");
            docId = ((Number) rows.documentsOf(runId).get(0).get("DOC_ID")).longValue();
            await().atMost(TrackingRows.TIMEOUT).until(() -> "SEND_FAILED".equals(status(docId)));
            Map<String, Object> failed = rows.document(docId);
            assertThat(failed.get("LAST_ERROR")).asString().isNotBlank();
            assertThat(((Timestamp) failed.get("NEXT_ATTEMPT_AT")).toInstant()).isAfter(clock.now()
                    .minus(Duration.ofSeconds(5)));
        } finally {
            broker.start();
        }
        await().atMost(TrackingRows.TIMEOUT).until(() -> "SENT".equals(status(docId)));
        assertThat(((Number) rows.document(docId).get("SEND_ATTEMPTS")).intValue()).isGreaterThanOrEqualTo(2);
        assertThat(receive(docId)).isNotNull();
    }

    @Test
    void anOlderDocumentIsSupersededAndNeverSent() throws Exception {
        Instant now = clock.now();
        long oldRun = rows.insertRun("DISPATCHING", now);
        long newRun = rows.insertRun("DISPATCHING", now);
        // The older document is parked in a live lease until the newer one exists.
        long older = rows.insertDocument(oldRun, "90101", "GENERATING", 1, "test", now.plus(Duration.ofHours(1)),
                now);
        long newer = rows.insertDocument(newRun, "90101", "SENT", 1, null, null, now);
        rows.storeContent(older, PDF, sha256(PDF), now);
        jdbc.update("UPDATE REPORT_DOCUMENT SET STATUS = 'GENERATED', LEASE_OWNER = NULL, LEASE_EXPIRES_AT = NULL "
                + "WHERE DOC_ID = ?", older);

        await().atMost(TrackingRows.TIMEOUT).until(() -> "SUPERSEDED".equals(status(older)));
        assertThat(rows.document(older).get("JMS_MESSAGE_ID")).isNull();
        assertThat(browse(older)).isEmpty();
        assertThat(status(newer)).isEqualTo("SENT");

        ResponseEntity<JsonNode> refused = post("/api/v1/report-documents/" + older + "/retransmit");
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("type").asText()).endsWith("/retransmit-not-allowed");
    }

    @Test
    void retransmittingOneDocumentResendsTheStoredPdfAndReopensTheRun() throws Exception {
        Instant now = clock.now();
        long runId = rows.insertRun("COMPLETED_WITH_ERRORS", now);
        long docId = rows.insertDocument(runId, "90201", "SEND_FAILED_FINAL", 1, null, null, now);
        rows.storeContent(docId, PDF, sha256(PDF), now);
        jdbc.update("UPDATE REPORT_DOCUMENT SET SEND_ATTEMPTS = 8 WHERE DOC_ID = ?", docId);

        ResponseEntity<JsonNode> accepted = post("/api/v1/report-documents/" + docId + "/retransmit");

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(accepted.getHeaders().getLocation()).hasToString("/api/v1/report-documents/" + docId);
        assertThat(accepted.getBody().get("status").asText()).isIn("SEND_FAILED", "SENDING", "SENT");
        await().atMost(TrackingRows.TIMEOUT).until(() -> "SENT".equals(status(docId)));
        rows.awaitRunStatus(runId, "COMPLETED");
        BytesMessage message = (BytesMessage) receive(docId);
        byte[] body = new byte[(int) message.getBodyLength()];
        message.readBytes(body);
        assertThat(body).isEqualTo(PDF);
        assertThat(((Number) rows.document(docId).get("SEND_ATTEMPTS")).intValue()).isEqualTo(1);

        assertThat(post("/api/v1/report-documents/" + docId + "/retransmit").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("/api/v1/report-documents/999999/retransmit").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void retransmittingARunRequeuesItsFailedDocumentsWithStoredContent() {
        Instant now = clock.now();
        long runId = rows.insertRun("COMPLETED_WITH_ERRORS", now);
        long first = rows.insertDocument(runId, "90301", "SEND_FAILED_FINAL", 8, null, null, now);
        long second = rows.insertDocument(runId, "90302", "SEND_FAILED", 2, null, null, now);
        long purged = rows.insertDocument(runId, "90303", "SEND_FAILED_FINAL", 8, null, null, now);
        rows.storeContent(first, PDF, sha256(PDF), now);
        rows.storeContent(second, PDF, sha256(PDF), now);
        jdbc.update("UPDATE REPORT_DOCUMENT SET NEXT_ATTEMPT_AT = ? WHERE DOC_ID = ?",
                Timestamp.from(now.plus(Duration.ofHours(1))), second);
        jdbc.update("UPDATE REPORT_DOCUMENT SET CONTENT_PURGED_AT = ? WHERE DOC_ID = ?", Timestamp.from(now), purged);

        ResponseEntity<JsonNode> accepted = post("/api/v1/report-runs/" + runId + "/retransmit-failed");

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(accepted.getBody().get("requeued").asInt()).isEqualTo(2);
        await().atMost(TrackingRows.TIMEOUT).until(() -> "SENT".equals(status(first)) && "SENT".equals(status(second)));
        assertThat(status(purged)).isEqualTo("SEND_FAILED_FINAL");
        rows.awaitRunStatus(runId, "COMPLETED_WITH_ERRORS");
        assertThat(post("/api/v1/report-runs/999999/retransmit-failed").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void purgeDeletesOldContentKeepsMetadataAndDownloadsReturn410() {
        Instant now = clock.now();
        Instant old = now.minus(Duration.ofDays(91));
        long runId = rows.insertRun("COMPLETED", now);
        long oldSent = rows.insertDocument(runId, "90401", "SENT", 1, null, null, now);
        long recentSent = rows.insertDocument(runId, "90402", "SENT", 1, null, null, now);
        long oldSuperseded = rows.insertDocument(runId, "90403", "SUPERSEDED", 1, null, null, now);
        long oldFailed = rows.insertDocument(runId, "90404", "SEND_FAILED_FINAL", 8, null, null, now);
        for (long docId : List.of(oldSent, recentSent, oldSuperseded, oldFailed)) {
            rows.storeContent(docId, PDF, sha256(PDF), old);
        }
        jdbc.update("UPDATE REPORT_DOCUMENT SET SENT_AT = ? WHERE DOC_ID = ?", Timestamp.from(old), oldSent);
        jdbc.update("UPDATE REPORT_DOCUMENT SET SENT_AT = ? WHERE DOC_ID = ?", Timestamp.from(now), recentSent);
        jdbc.update("UPDATE REPORT_DOCUMENT SET UPDATED_AT = ? WHERE DOC_ID IN (?, ?)", Timestamp.from(old),
                oldSuperseded, oldFailed);

        purgeJob.purge();

        for (long purged : List.of(oldSent, oldSuperseded)) {
            assertThat(contentRows(purged)).isZero();
            assertThat(rows.document(purged).get("CONTENT_PURGED_AT")).isNotNull();
            assertThat(rows.document(purged).get("SHA256")).isNotNull();
            assertThat(http.getForEntity("/api/v1/report-documents/" + purged + "/content", JsonNode.class)
                    .getStatusCode()).isEqualTo(HttpStatus.GONE);
        }
        for (long kept : List.of(recentSent, oldFailed)) {
            assertThat(contentRows(kept)).isOne();
            assertThat(rows.document(kept).get("CONTENT_PURGED_AT")).isNull();
        }
        jdbc.update("UPDATE REPORT_DOCUMENT SET STATUS = 'SEND_FAILED_FINAL' WHERE DOC_ID = ?", oldSent);
        ResponseEntity<JsonNode> gone = post("/api/v1/report-documents/" + oldSent + "/retransmit");
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(gone.getBody().get("type").asText()).endsWith("/content-purged");
        assertThat(purgeJob.purge()).as("a second pass finds nothing").isZero();
    }

    private long startRun(String fips) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = http.postForEntity("/api/v1/report-runs",
                new HttpEntity<>("{\"stateCode\":\"VA\",\"fipsCodes\":[\"" + fips + "\"]}", headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return response.getBody().get("runId").asLong();
    }

    private ResponseEntity<JsonNode> post(String path) {
        return http.postForEntity(path, HttpEntity.EMPTY, JsonNode.class);
    }

    private String status(long docId) {
        return ((String) rows.document(docId).get("STATUS")).trim();
    }

    private int contentRows(long docId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM REPORT_CONTENT WHERE DOC_ID = ?", Integer.class, docId);
    }

    private Message receive(long docId) throws Exception {
        try (Connection connection = connectionFactory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            connection.start();
            return session.createConsumer(session.createQueue(QUEUE), "reportDocId = " + docId).receive(10_000);
        }
    }

    private List<Message> browse(long docId) throws Exception {
        try (Connection connection = connectionFactory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            QueueBrowser browser = session.createBrowser(session.createQueue(QUEUE), "reportDocId = " + docId);
            List<Message> found = new ArrayList<>();
            Enumeration<?> messages = browser.getEnumeration();
            while (messages.hasMoreElements()) {
                found.add((Message) messages.nextElement());
            }
            return found;
        }
    }

    private static String sha256(byte[] bytes) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return IntStream.range(0, digest.length).mapToObj(i -> String.format("%02x", digest[i]))
                .collect(Collectors.joining());
    }
}
