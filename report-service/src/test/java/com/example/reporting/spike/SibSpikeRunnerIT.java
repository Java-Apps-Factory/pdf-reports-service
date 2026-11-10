package com.example.reporting.spike;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reporting.transmission.ReportMessageProperties;
import com.ibm.websphere.sib.api.jms.JmsFactoryFactory;
import com.ibm.websphere.sib.api.jms.JmsFactoryFactory.RecordingFactory;
import com.ibm.websphere.sib.api.jms.MockSibBus;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

/**
 * The whole {@code sib-spike} profile on a laptop: Spring builds the SIB connection factory and queue through
 * the thin-client API (here the test stand-in backed by {@link MockSibBus}), {@link SibSpikeRunner} sends at
 * startup with credentials, and the message arrives with every contract property and an intact body.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, properties = {
    "reporting.sib.bus-name=ReportBus",
    "reporting.sib.provider-endpoints=was-staging:7276:BootstrapBasicMessaging",
    "reporting.sib.username=svc-report",
    "reporting.sib.password=not-a-real-secret",
    "reporting.sib.spike-payload-bytes=2097152"
})
@ActiveProfiles({"h2", "test", "sib", "sib-spike"})
class SibSpikeRunnerIT {

    private static final int PAYLOAD_BYTES = 2 * 1024 * 1024;
    private static final long RECEIVE_TIMEOUT_MS = 10_000;

    @Test
    void spikeSendsOneContractMessageThroughTheSibCodePathAtStartup() throws JMSException {
        RecordingFactory sib = JmsFactoryFactory.lastCreated();
        assertThat(sib.busName()).isEqualTo("ReportBus");
        assertThat(sib.providerEndpoints()).isEqualTo("was-staging:7276:BootstrapBasicMessaging");
        assertThat(sib.targetTransportChain()).isEqualTo("InboundBasicMessaging");
        assertThat(sib.lastUser()).isEqualTo("svc-report");

        try (ActiveMQConnectionFactory downstream = new ActiveMQConnectionFactory(MockSibBus.URL)) {
            Connection connection = downstream.createConnection();
            try {
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                connection.start();
                Message received = session.createConsumer(session.createQueue("REPORT.OUT.QUEUE"))
                        .receive(RECEIVE_TIMEOUT_MS);

                assertThat(received).isInstanceOf(BytesMessage.class);
                BytesMessage body = (BytesMessage) received;
                byte[] bytes = new byte[(int) body.getBodyLength()];
                body.readBytes(bytes);
                assertThat(bytes).hasSize(PAYLOAD_BYTES).isEqualTo(SibSpikeSender.samplePayload(PAYLOAD_BYTES));
                assertThat(received.getStringProperty(ReportMessageProperties.SHA256))
                        .isEqualTo(SibSpikeSender.sha256Hex(bytes));
                assertThat(received.getStringProperty(ReportMessageProperties.STATE_CODE)).isEqualTo("VA");
                assertThat(received.getStringProperty(ReportMessageProperties.FIPS_CODE)).isEqualTo("51059");
                assertThat(received.getLongProperty(ReportMessageProperties.REPORT_DOC_ID)).isEqualTo(48213L);
                assertThat(received.getStringProperty(ReportMessageProperties.CONTENT_TYPE))
                        .isEqualTo("application/pdf");
            } finally {
                connection.close();
            }
        }
    }
}
