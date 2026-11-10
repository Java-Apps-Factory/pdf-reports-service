package com.ibm.websphere.sib.api.jms;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Queue;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

/**
 * Test stand-in for the SIB {@code JmsFactoryFactory}. Connection factories delegate to the in-VM mock
 * broker and remember the SIB settings they were given, so tests can assert on them.
 *
 * <p>A bus name of {@value #FAILING_BUS} makes {@code setBusName} throw, imitating a thin-client error.
 */
public final class JmsFactoryFactory {

    /** Bus name that makes the fake factory fail. */
    public static final String FAILING_BUS = "FAIL";

    private static final JmsFactoryFactory INSTANCE = new JmsFactoryFactory();
    private static final AtomicReference<RecordingFactory> LAST = new AtomicReference<>();

    private JmsFactoryFactory() {
    }

    /**
     * Returns the singleton, like the IBM API.
     *
     * @return the factory factory
     */
    public static JmsFactoryFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the most recently created connection factory.
     *
     * @return the factory, or {@code null} if none was created
     */
    public static RecordingFactory lastCreated() {
        return LAST.get();
    }

    /**
     * Creates a connection factory that sends to the embedded Artemis broker.
     *
     * @return a new factory
     */
    public JmsConnectionFactory createConnectionFactory() {
        MockSibBus.ensureStarted();
        RecordingFactory factory = new RecordingFactory(new ActiveMQConnectionFactory(MockSibBus.URL));
        LAST.set(factory);
        return factory;
    }

    /**
     * Creates a queue reference.
     *
     * @param name queue name
     * @return an Artemis queue with that name
     */
    public Queue createQueue(String name) {
        return ActiveMQJMSClient.createQueue(name);
    }

    /** Connection factory that records the SIB settings and the credentials used. */
    public static final class RecordingFactory implements JmsConnectionFactory {
        private final ConnectionFactory target;
        private volatile String busName;
        private volatile String providerEndpoints;
        private volatile String targetTransportChain;
        private volatile String lastUser;

        RecordingFactory(ConnectionFactory target) {
            this.target = target;
        }

        @Override
        public void setBusName(String value) {
            if (FAILING_BUS.equals(value)) {
                throw new IllegalArgumentException("CWSIA0241E: simulated thin-client failure");
            }
            this.busName = value;
        }

        @Override
        public void setProviderEndpoints(String value) {
            this.providerEndpoints = value;
        }

        @Override
        public void setTargetTransportChain(String value) {
            this.targetTransportChain = value;
        }

        @Override
        public Connection createConnection() throws JMSException {
            lastUser = null;
            return target.createConnection();
        }

        @Override
        public Connection createConnection(String user, String password) throws JMSException {
            lastUser = Objects.requireNonNull(user, "user");
            // The embedded broker runs without security; credentials are recorded, not checked.
            return target.createConnection();
        }

        @Override
        public JMSContext createContext() {
            throw jms11Only();
        }

        @Override
        public JMSContext createContext(String user, String password) {
            throw jms11Only();
        }

        @Override
        public JMSContext createContext(String user, String password, int sessionMode) {
            throw jms11Only();
        }

        @Override
        public JMSContext createContext(int sessionMode) {
            throw jms11Only();
        }

        private static UnsupportedOperationException jms11Only() {
            return new UnsupportedOperationException("The SIB thin client is a JMS 1.1 provider: no JMSContext");
        }

        /**
         * Returns the bus name set.
         *
         * @return bus name
         */
        public String busName() {
            return busName;
        }

        /**
         * Returns the endpoints set.
         *
         * @return endpoints
         */
        public String providerEndpoints() {
            return providerEndpoints;
        }

        /**
         * Returns the transport chain set.
         *
         * @return chain
         */
        public String targetTransportChain() {
            return targetTransportChain;
        }

        /**
         * Returns the user of the last connection, or {@code null} when none was given.
         *
         * @return user
         */
        public String lastUser() {
            return lastUser;
        }
    }
}
