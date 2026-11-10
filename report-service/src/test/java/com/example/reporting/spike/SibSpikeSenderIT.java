package com.example.reporting.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.transmission.ReportMessageProperties;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Session;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * Exercises the spike sender against the embedded Artemis broker: the same JMS 1.1 calls and
 * message contract that the real SIB spike uses.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"h2", "test"})
@TestConstructor(autowireMode = AutowireMode.ALL)
class SibSpikeSenderIT {

    private static final int PAYLOAD_BYTES = 1024 * 1024;
    private static final long RECEIVE_TIMEOUT_MS = 10_000;

    private final ConnectionFactory connectionFactory;
    private final ReportingProperties properties;

    SibSpikeSenderIT(ConnectionFactory connectionFactory, ReportingProperties properties) {
        this.connectionFactory = connectionFactory;
        this.properties = properties;
    }

    @Test
    void sendsPersistentBytesMessageWithAllContractProperties() throws JMSException {
        byte[] payload = SibSpikeSender.samplePayload(PAYLOAD_BYTES);
        OffsetDateTime now = OffsetDateTime.parse("2026-11-10T02:03:11.123-05:00");
        Connection connection = connectionFactory.createConnection();
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(properties.getTransmission().getQueueName());

            String messageId = new SibSpikeSender(connectionFactory, queue).send(payload, now);

            connection.start();
            Message received = session.createConsumer(queue).receive(RECEIVE_TIMEOUT_MS);
            assertThat(received).isInstanceOf(BytesMessage.class);
            assertThat(received.getJMSMessageID()).isEqualTo(messageId);
            assertThat(received.getJMSDeliveryMode()).isEqualTo(DeliveryMode.PERSISTENT);
            assertThat(received.getLongProperty(ReportMessageProperties.REPORT_DOC_ID)).isEqualTo(48213L);
            assertThat(received.getLongProperty(ReportMessageProperties.RUN_ID)).isEqualTo(1702L);
            assertThat(received.getStringProperty(ReportMessageProperties.STATE_CODE)).isEqualTo("VA");
            assertThat(received.getStringProperty(ReportMessageProperties.FIPS_CODE)).isEqualTo("51059");
            assertThat(received.getStringProperty(ReportMessageProperties.GENERATED_AT))
                    .isEqualTo("2026-11-10T02:03:11-05:00");
            assertThat(received.getStringProperty(ReportMessageProperties.DATA_AS_OF))
                    .isEqualTo("2026-11-10T02:03:11-05:00");
            assertThat(received.getIntProperty(ReportMessageProperties.RECORD_COUNT)).isEqualTo(1240);
            assertThat(received.getIntProperty(ReportMessageProperties.PAGE_COUNT)).isEqualTo(31);
            assertThat(received.getStringProperty(ReportMessageProperties.SHA256))
                    .isEqualTo(SibSpikeSender.sha256Hex(payload));
            assertThat(received.getStringProperty(ReportMessageProperties.CONTENT_TYPE)).isEqualTo("application/pdf");
            assertThat(received.getIntProperty(ReportMessageProperties.SCHEMA_VERSION)).isEqualTo(1);

            BytesMessage body = (BytesMessage) received;
            byte[] bytes = new byte[(int) body.getBodyLength()];
            body.readBytes(bytes);
            assertThat(bytes).isEqualTo(payload);
        } finally {
            connection.close();
        }
    }

    @Test
    void sha256HexMatchesAKnownVector() {
        assertThat(SibSpikeSender.sha256Hex("test".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
    }

    @Test
    void rejectsPayloadSmallerThanThePdfSignature() {
        assertThatThrownBy(() -> SibSpikeSender.samplePayload(3)).isInstanceOf(IllegalArgumentException.class);
    }
}
