package com.example.reporting.spike;

import com.example.reporting.profile.ReportingProperties;
import java.lang.reflect.InvocationTargetException;
import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.util.StringUtils;

/**
 * Builds the SIB connection factory and queue for the Phase 0 spike (profile {@code sib-spike}).
 *
 * <p>The IBM thin client is not on Maven Central, so the spike calls it reflectively and the
 * normal build needs no IBM jar. Build the WAR with {@code -Psib} (or drop the thin-client jar into
 * the classpath) to run it. Phase 4 replaces this class with {@code SibConnectionFactoryConfig}.
 */
@Configuration(proxyBeanMethods = false)
@Profile("sib-spike")
public class SibSpikeConfig {

    private static final String FACTORY_FACTORY = "com.ibm.websphere.sib.api.jms.JmsFactoryFactory";
    private static final String CONNECTION_FACTORY = "com.ibm.websphere.sib.api.jms.JmsConnectionFactory";

    /**
     * Creates the SIB connection factory, wrapped with credentials when a user is configured.
     *
     * @param sib SIB connection settings
     * @return the connection factory used by the spike
     */
    @Bean
    public ConnectionFactory sibSpikeConnectionFactory(SibSpikeProperties sib) {
        requireText(sib.getBusName(), "reporting.sib.bus-name");
        requireText(sib.getProviderEndpoints(), "reporting.sib.provider-endpoints");
        ConnectionFactory target = createThinClientFactory(sib);
        if (!StringUtils.hasText(sib.getUsername())) {
            return target;
        }
        UserCredentialsConnectionFactoryAdapter adapter = new UserCredentialsConnectionFactoryAdapter();
        adapter.setTargetConnectionFactory(target);
        adapter.setUsername(sib.getUsername());
        adapter.setPassword(sib.getPassword());
        return adapter;
    }

    /**
     * Creates the SIB destination queue.
     *
     * @param properties reporting configuration holding the queue name
     * @return the queue the spike sends to
     */
    @Bean
    public Queue sibSpikeQueue(ReportingProperties properties) {
        try {
            Class<?> factoryFactoryType = Class.forName(FACTORY_FACTORY);
            Object factoryFactory = factoryFactoryType.getMethod("getInstance").invoke(null);
            return (Queue) factoryFactoryType.getMethod("createQueue", String.class)
                    .invoke(factoryFactory, properties.getTransmission().getQueueName());
        } catch (ReflectiveOperationException e) {
            throw thinClientFailure(e);
        }
    }

    private static ConnectionFactory createThinClientFactory(SibSpikeProperties sib) {
        try {
            Class<?> factoryFactoryType = Class.forName(FACTORY_FACTORY);
            Class<?> connectionFactoryType = Class.forName(CONNECTION_FACTORY);
            Object factoryFactory = factoryFactoryType.getMethod("getInstance").invoke(null);
            Object factory = factoryFactoryType.getMethod("createConnectionFactory").invoke(factoryFactory);
            set(connectionFactoryType, factory, "setBusName", sib.getBusName());
            set(connectionFactoryType, factory, "setProviderEndpoints", sib.getProviderEndpoints());
            set(connectionFactoryType, factory, "setTargetTransportChain", sib.getTargetTransportChain());
            return (ConnectionFactory) factory;
        } catch (ReflectiveOperationException e) {
            throw thinClientFailure(e);
        }
    }

    private static void set(Class<?> type, Object target, String setter, String value)
            throws ReflectiveOperationException {
        type.getMethod(setter, String.class).invoke(target, value);
    }

    private static void requireText(String value, String property) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Profile sib-spike needs " + property + " (activate profile sib too)");
        }
    }

    private static IllegalStateException thinClientFailure(ReflectiveOperationException e) {
        Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
        return new IllegalStateException(
                "Cannot use the IBM SIB thin client. Is com.ibm.ws.sib.client.thin.jms_9.0.0.jar on the "
                        + "classpath (build with -Psib)? Cause: " + cause, cause);
    }
}
