package com.example.reporting.config;

import com.example.reporting.profile.ReportingProperties;
import java.lang.reflect.InvocationTargetException;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.util.StringUtils;

/**
 * WebSphere SIB connection (profile {@code sib}, SPEC section 13): the IBM thin client's connection factory,
 * wrapped in {@link UserCredentialsConnectionFactoryAdapter} when a user is configured and then in
 * {@link CachingConnectionFactory}.
 *
 * <p>The thin client is not on Maven Central, so it is called reflectively and only the {@code -Psib} build
 * (or the server's classpath) provides it.
 */
@Configuration(proxyBeanMethods = false)
@Profile("sib")
public class SibConnectionFactoryConfig {

    private static final String FACTORY_FACTORY = "com.ibm.websphere.sib.api.jms.JmsFactoryFactory";
    private static final String CONNECTION_FACTORY = "com.ibm.websphere.sib.api.jms.JmsConnectionFactory";

    /**
     * Creates the cached, credentialed SIB connection factory.
     *
     * @param properties reporting configuration ({@code reporting.sib.*})
     * @return the connection factory used by the report {@code JmsTemplate}
     */
    @Bean
    public CachingConnectionFactory sibConnectionFactory(ReportingProperties properties) {
        ReportingProperties.Sib sib = properties.getSib();
        requireSetting(sib.getBusName(), "reporting.sib.bus-name (SIB_BUS)");
        requireSetting(sib.getProviderEndpoints(), "reporting.sib.provider-endpoints (SIB_ENDPOINTS)");
        ConnectionFactory target = thinClientFactory(sib);
        if (StringUtils.hasText(sib.getUsername())) {
            UserCredentialsConnectionFactoryAdapter credentials = new UserCredentialsConnectionFactoryAdapter();
            credentials.setTargetConnectionFactory(target);
            credentials.setUsername(sib.getUsername());
            credentials.setPassword(sib.getPassword());
            target = credentials;
        }
        CachingConnectionFactory cached = new CachingConnectionFactory(target);
        cached.setReconnectOnException(true);
        return cached;
    }

    /**
     * Creates the SIB report queue.
     *
     * @param properties reporting configuration (queue name)
     * @return the queue
     */
    @Bean
    public Destination reportQueue(ReportingProperties properties) {
        try {
            Class<?> factoryFactoryType = Class.forName(FACTORY_FACTORY);
            Object factoryFactory = factoryFactoryType.getMethod("getInstance").invoke(null);
            return (Destination) factoryFactoryType.getMethod("createQueue", String.class)
                    .invoke(factoryFactory, properties.getTransmission().getQueueName());
        } catch (ReflectiveOperationException e) {
            throw thinClientFailure(e);
        }
    }

    private static ConnectionFactory thinClientFactory(ReportingProperties.Sib sib) {
        try {
            Class<?> factoryFactoryType = Class.forName(FACTORY_FACTORY);
            Class<?> connectionFactoryType = Class.forName(CONNECTION_FACTORY);
            Object factoryFactory = factoryFactoryType.getMethod("getInstance").invoke(null);
            Object factory = factoryFactoryType.getMethod("createConnectionFactory").invoke(factoryFactory);
            connectionFactoryType.getMethod("setBusName", String.class).invoke(factory, sib.getBusName());
            connectionFactoryType.getMethod("setProviderEndpoints", String.class)
                    .invoke(factory, sib.getProviderEndpoints());
            connectionFactoryType.getMethod("setTargetTransportChain", String.class)
                    .invoke(factory, sib.getTargetTransportChain());
            return (ConnectionFactory) factory;
        } catch (ReflectiveOperationException e) {
            throw thinClientFailure(e);
        }
    }

    private static void requireSetting(String value, String property) {
        if (!StringUtils.hasText(value) || value.contains("${")) {
            throw new IllegalStateException("Profile sib needs " + property);
        }
    }

    private static IllegalStateException thinClientFailure(ReflectiveOperationException e) {
        Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
        return new IllegalStateException("Cannot use the IBM SIB thin client. Is "
                + "com.ibm.ws.sib.client.thin.jms_9.0.0.jar on the classpath (build with -Psib)? Cause: " + cause,
                cause);
    }
}
