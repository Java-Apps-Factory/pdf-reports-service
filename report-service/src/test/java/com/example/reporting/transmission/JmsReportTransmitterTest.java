package com.example.reporting.transmission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;
import org.junit.jupiter.api.Test;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;

class JmsReportTransmitterTest {

    private final JmsTemplate template = mock(JmsTemplate.class);
    private final Destination queue = mock(Destination.class);
    private final Session session = mock(Session.class);
    private final BytesMessage message = mock(BytesMessage.class);

    @Test
    void sendsOneBytesMessageToTheReportQueueAndReturnsItsId() throws JMSException {
        when(session.createBytesMessage()).thenReturn(message);
        when(message.getJMSMessageID()).thenReturn("ID:7");
        doAnswer(call -> ((MessageCreator) call.getArgument(1)).createMessage(session))
                .when(template).send(eq(queue), any(MessageCreator.class));

        String id = new JmsReportTransmitter(template, queue).send(sample());

        assertThat(id).isEqualTo("ID:7");
        verify(message).writeBytes(new byte[] {1, 2, 3});
        verify(message).setLongProperty(ReportMessageProperties.REPORT_DOC_ID, 9L);
    }

    @Test
    void aMissingMessageIdIsAJmsError() throws JMSException {
        when(session.createBytesMessage()).thenReturn(message);
        when(message.getJMSMessageID()).thenThrow(new JMSException("closed"));
        doAnswer(call -> ((MessageCreator) call.getArgument(1)).createMessage(session))
                .when(template).send(eq(queue), any(MessageCreator.class));

        assertThatThrownBy(() -> new JmsReportTransmitter(template, queue).send(sample()))
                .isInstanceOf(UncategorizedJmsException.class);
    }

    private static ReportMessage sample() {
        return ReportMessage.builder().identity(9L, 2L, "VA", "51059")
                .times("2026-11-10T02:03:11-05:00", "2026-11-10T02:00:00-05:00")
                .content(5, 1, "abc", new byte[] {1, 2, 3}).build();
    }
}
