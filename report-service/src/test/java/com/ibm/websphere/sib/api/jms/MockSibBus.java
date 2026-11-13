package com.ibm.websphere.sib.api.jms;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;

/**
 * An in-VM Artemis broker that plays the SIB messaging engine in tests and local runs.
 *
 * <p>Uses in-VM acceptor id 9701, far above the ids Spring Boot gives embedded brokers (0, 1, 2, ... per
 * application context), so the two never collide in one JVM. Started lazily by {@link JmsFactoryFactory}; non-persistent, no security.
 */
public final class MockSibBus {

    /** Client URL of the mock bus. */
    public static final String URL = "vm://9701";

    private static EmbeddedActiveMQ broker;

    private MockSibBus() {
    }

    /** Starts the broker once per JVM. */
    public static synchronized void ensureStarted() {
        if (broker != null) {
            return;
        }
        try {
            Path dir = Files.createTempDirectory("mock-sib");
            Configuration config = new ConfigurationImpl()
                    .setName("mock-sib")
                    .setPersistenceEnabled(false)
                    .setSecurityEnabled(false)
                    .setJMXManagementEnabled(false)
                    .setLargeMessagesDirectory(dir.resolve("large").toString())
                    .setPagingDirectory(dir.resolve("paging").toString())
                    .addAcceptorConfiguration("invm", URL);
            EmbeddedActiveMQ server = new EmbeddedActiveMQ();
            server.setConfiguration(config);
            server.start();
            broker = server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new IllegalStateException("Mock SIB bus did not start", e);
        }
    }
}
