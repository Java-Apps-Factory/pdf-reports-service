package com.example.reporting.transmission;

import java.util.concurrent.atomic.AtomicReference;
import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.JmsUtils;
import org.springframework.stereotype.Component;

/**
 * Sends a report as one persistent {@code BytesMessage} in a transacted session (SPEC section 13), using JMS
 * 1.1 calls only. The broker is hidden behind the {@code JmsTemplate} and {@code reportQueue} beans.
 */
@Component
public class JmsReportTransmitter implements ReportTransmitter {

    private final JmsTemplate jmsTemplate;
    private final Destination queue;

    /**
     * Creates the transmitter.
     *
     * @param jmsTemplate transacted, persistent template from {@code JmsConfig}
     * @param queue the report queue
     */
    public JmsReportTransmitter(JmsTemplate jmsTemplate, @Qualifier("reportQueue") Destination queue) {
        this.jmsTemplate = jmsTemplate;
        this.queue = queue;
    }

    @Override
    public String send(ReportMessage message) {
        AtomicReference<Message> sent = new AtomicReference<>();
        jmsTemplate.send(queue, session -> {
            BytesMessage bytes = session.createBytesMessage();
            message.writeTo(bytes);
            sent.set(bytes);
            return bytes;
        });
        try {
            return sent.get().getJMSMessageID();
        } catch (JMSException e) {
            throw JmsUtils.convertJmsAccessException(e);
        }
    }
}
