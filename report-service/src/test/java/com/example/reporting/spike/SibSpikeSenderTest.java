package com.example.reporting.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.reporting.transmission.ReportMessageProperties;
import java.time.OffsetDateTime;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * The exact JMS 1.1 call sequence the SIB thin client sees, verified with mocks (no broker): transacted
 * session, persistent send, commit, and the connection always closed.
 */
class SibSpikeSenderTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-10T02:00:00-05:00");

    private final ConnectionFactory factory = mock(ConnectionFactory.class);
    private final Connection connection = mock(Connection.class);
    private final Session session = mock(Session.class);
    private final MessageProducer producer = mock(MessageProducer.class);
    private final BytesMessage message = mock(BytesMessage.class);
    private final Queue queue = mock(Queue.class);

    @BeforeEach
    void wireMocks() throws JMSException {
        when(factory.createConnection()).thenReturn(connection);
        when(connection.createSession(true, Session.SESSION_TRANSACTED)).thenReturn(session);
        when(session.createProducer(queue)).thenReturn(producer);
        when(session.createBytesMessage()).thenReturn(message);
        when(message.getJMSMessageID()).thenReturn("ID:sib-0001");
    }

    @Test
    void sendsPersistentlyInATransactedSessionThenCommitsAndCloses() throws JMSException {
        byte[] payload = SibSpikeSender.samplePayload(64);

        String id = new SibSpikeSender(factory, queue).send(payload, NOW);

        assertThat(id).isEqualTo("ID:sib-0001");
        InOrder order = inOrder(connection, session, producer, message);
        order.verify(connection).createSession(true, Session.SESSION_TRANSACTED);
        order.verify(message).writeBytes(payload);
        order.verify(producer).send(message, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY,
                Message.DEFAULT_TIME_TO_LIVE);
        order.verify(session).commit();
        order.verify(connection).close();
        verify(message).setStringProperty(ReportMessageProperties.FIPS_CODE, "51059");
        verify(message).setStringProperty(ReportMessageProperties.SHA256, SibSpikeSender.sha256Hex(payload));
        verify(message).setStringProperty(ReportMessageProperties.GENERATED_AT, "2026-11-10T02:00:00-05:00");
        verify(message).setLongProperty(ReportMessageProperties.REPORT_DOC_ID, SibSpikeSender.SAMPLE_DOC_ID);
        verify(message).setIntProperty(ReportMessageProperties.SCHEMA_VERSION,
                ReportMessageProperties.CURRENT_SCHEMA_VERSION);
        // Connection is never started: producing needs no start, and SIB charges a consumer thread for it.
        verify(connection, never()).start();
    }

    @Test
    void brokerRejectionPropagatesWithoutCommitAndStillClosesTheConnection() throws JMSException {
        doThrow(new JMSException("CWSIK0025E: message too large")).when(producer)
                .send(any(Message.class), anyInt(), anyInt(), anyLong());

        assertThatThrownBy(() -> new SibSpikeSender(factory, queue).send(SibSpikeSender.samplePayload(64), NOW))
                .isInstanceOf(JMSException.class)
                .hasMessageContaining("CWSIK0025E");
        verify(session, never()).commit();
        verify(connection).close();
    }

    @Test
    void connectionFailureIsReportedAsIs() throws JMSException {
        when(factory.createConnection()).thenThrow(new JMSException("CWSIA0241E: bus not reachable"));

        assertThatThrownBy(() -> new SibSpikeSender(factory, queue).send(SibSpikeSender.samplePayload(64), NOW))
                .isInstanceOf(JMSException.class)
                .hasMessageContaining("CWSIA0241E");
        verify(connection, never()).close();
        verify(message, never()).setStringProperty(anyString(), eq("51059"));
    }
}
