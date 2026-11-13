package com.example.reporting.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reporting.TrackingRows;
import com.example.reporting.orchestration.ReportRunService;
import com.example.reporting.orchestration.StartRunCommand;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.TriggerType;
import com.example.reporting.transmission.ReportMessageProperties;
import com.ibm.websphere.sib.api.jms.JmsFactoryFactory;
import com.ibm.websphere.sib.api.jms.JmsFactoryFactory.RecordingFactory;
import com.ibm.websphere.sib.api.jms.MockSibBus;
import java.util.List;
import java.util.Map;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.Session;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * The {@code sib} profile end to end on a laptop: {@link SibConnectionFactoryConfig} builds the connection
 * through the thin-client API (here the test stand-in backed by {@link MockSibBus}, since WebSphere runs only on
 * staging), and a real run is generated and SENT to the "SIB" queue with the contract properties.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, properties = {
    "spring.datasource.url=jdbc:h2:mem:sibprofile;MODE=DB2;DATABASE_TO_UPPER=TRUE;DB_CLOSE_DELAY=-1;"
            + "DB_CLOSE_ON_EXIT=FALSE",
    "reporting.sib.bus-name=ReportBus",
    "reporting.sib.provider-endpoints=was-staging:7276:BootstrapBasicMessaging",
    "reporting.sib.username=svc-report",
    "reporting.sib.password=not-a-real-secret"
})
@ActiveProfiles({"h2", "test", "sib"})
@TestConstructor(autowireMode = AutowireMode.ALL)
class SibProfileIT {

    private final ReportRunService runService;
    private final ConnectionFactory connectionFactory;
    private final JdbcTemplate jdbc;

    SibProfileIT(ReportRunService runService, ConnectionFactory connectionFactory, JdbcTemplate jdbc) {
        this.runService = runService;
        this.connectionFactory = connectionFactory;
        this.jdbc = jdbc;
    }

    @Test
    void aRunIsSentThroughTheSibConnectionFactory() throws Exception {
        assertThat(connectionFactory).isInstanceOf(CachingConnectionFactory.class);
        RecordingFactory sib = JmsFactoryFactory.lastCreated();
        assertThat(sib.busName()).isEqualTo("ReportBus");
        assertThat(sib.providerEndpoints()).isEqualTo("was-staging:7276:BootstrapBasicMessaging");

        ReportRun run = runService.startRun(StartRunCommand.builder().stateCode("VA").fipsCodes(List.of("51013"))
                .triggerType(TriggerType.API).requestedBy("sib-test").build());
        TrackingRows rows = new TrackingRows(jdbc);
        rows.awaitRunStatus(run.getRunId(), "COMPLETED");
        Map<String, Object> document = rows.documentsOf(run.getRunId()).get(0);
        long docId = ((Number) document.get("DOC_ID")).longValue();
        assertThat(sib.lastUser()).isEqualTo("svc-report");

        try (ActiveMQConnectionFactory downstream = new ActiveMQConnectionFactory(MockSibBus.URL);
                Connection connection = downstream.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            connection.start();
            Message message = session.createConsumer(session.createQueue("REPORT.OUT.QUEUE"),
                    "reportDocId = " + docId).receive(10_000);
            assertThat(message).isInstanceOf(BytesMessage.class);
            assertThat(message.getStringProperty(ReportMessageProperties.FIPS_CODE)).isEqualTo("51013");
            assertThat(message.getStringProperty(ReportMessageProperties.SHA256))
                    .isEqualTo(((String) rows.document(docId).get("SHA256")).trim());
            assertThat(((BytesMessage) message).getBodyLength())
                    .isEqualTo(((Number) rows.document(docId).get("SIZE_BYTES")).longValue());
        }
    }
}
