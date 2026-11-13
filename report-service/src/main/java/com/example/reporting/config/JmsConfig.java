package com.example.reporting.config;

import javax.jms.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;

/**
 * The report {@link JmsTemplate} (SPEC section 13): transacted session, explicit QoS, persistent delivery, no
 * time-to-live. The connection factory comes from {@link SibConnectionFactoryConfig} (profile {@code sib}) or
 * Spring Boot's embedded Artemis ({@link ArtemisDevConfig}).
 */
@Configuration(proxyBeanMethods = false)
public class JmsConfig {

    /**
     * Creates the template.
     *
     * @param connectionFactory the broker's connection factory
     * @return the template
     */
    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setSessionTransacted(true);
        template.setExplicitQosEnabled(true);
        template.setDeliveryPersistent(true);
        template.setTimeToLive(0);
        return template;
    }
}
