package com.example.reporting.config;

import com.example.reporting.profile.ReportingProperties;
import javax.jms.Destination;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Local and test broker: Spring Boot's embedded ActiveMQ Artemis ({@code spring.artemis.mode: embedded} in the
 * {@code h2} profile) provides the connection factory; this class adds the report queue. Used whenever the
 * {@code sib} profile is not active.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!sib")
public class ArtemisDevConfig {

    /**
     * Creates the report queue on Artemis.
     *
     * @param properties reporting configuration (queue name)
     * @return the queue
     */
    @Bean
    public Destination reportQueue(ReportingProperties properties) {
        return ActiveMQJMSClient.createQueue(properties.getTransmission().getQueueName());
    }
}
