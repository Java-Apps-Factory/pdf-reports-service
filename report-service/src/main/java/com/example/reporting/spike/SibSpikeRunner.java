package com.example.reporting.spike;

import java.time.Clock;
import java.time.OffsetDateTime;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Phase 0 spike: at startup, sends one 25 MB bytes message with all contract properties to the
 * real SIB queue (profile {@code sib-spike}). Throwaway; delete once Phase 4 lands.
 *
 * <p>Proves the connection from Tomcat 9 on the target JDK, that SIB accepts a 25 MB message,
 * and gives the downstream team a message to read the properties from.
 */
@Component
@Profile("sib-spike")
public final class SibSpikeRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SibSpikeRunner.class);

    private final SibSpikeSender sender;
    private final SibSpikeProperties properties;
    private final Clock clock;

    /**
     * Creates the runner.
     *
     * @param connectionFactory SIB connection factory from {@link SibSpikeConfig}
     * @param queue SIB queue from {@link SibSpikeConfig}
     * @param properties spike settings
     * @param clock application clock
     */
    public SibSpikeRunner(
            @Qualifier("sibSpikeConnectionFactory") ConnectionFactory connectionFactory,
            @Qualifier("sibSpikeQueue") Queue queue,
            SibSpikeProperties properties,
            Clock clock) {
        this.sender = new SibSpikeSender(connectionFactory, queue);
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        int size = properties.getSpikePayloadBytes();
        LOG.info("SIB spike: sending one {}-byte message", size);
        try {
            String messageId = sender.send(SibSpikeSender.samplePayload(size), OffsetDateTime.now(clock));
            LOG.info("SIB spike: broker accepted the message, JMSMessageID={}", messageId);
        } catch (JMSException e) {
            throw new IllegalStateException("SIB spike failed: " + e, e);
        }
    }
}
