package com.example.reporting.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.profile.TestProfiles;
import com.ibm.websphere.sib.api.jms.JmsFactoryFactory;
import com.ibm.websphere.sib.api.jms.JmsFactoryFactory.RecordingFactory;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import javax.jms.JMSException;
import javax.jms.Queue;
import org.junit.jupiter.api.Test;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;

/** {@link SibConnectionFactoryConfig} against the test stand-in of the IBM thin-client API. */
class SibConnectionFactoryConfigTest {

    private final SibConnectionFactoryConfig config = new SibConnectionFactoryConfig();

    @Test
    void cachesTheThinClientFactoryConfiguredFromProperties() {
        CachingConnectionFactory factory = config.sibConnectionFactory(properties("ReportBus", ""));

        assertThat(factory.getTargetConnectionFactory()).isInstanceOf(RecordingFactory.class);
        RecordingFactory sib = (RecordingFactory) factory.getTargetConnectionFactory();
        assertThat(sib.busName()).isEqualTo("ReportBus");
        assertThat(sib.providerEndpoints()).isEqualTo("was-staging:7276:BootstrapBasicMessaging");
        assertThat(sib.targetTransportChain()).isEqualTo("InboundBasicMessaging");
    }

    @Test
    void addsCredentialsWhenAUserIsConfigured() {
        CachingConnectionFactory factory = config.sibConnectionFactory(properties("ReportBus", "svc-report"));
        assertThat(factory.getTargetConnectionFactory()).isInstanceOf(UserCredentialsConnectionFactoryAdapter.class);
        assertThat(JmsFactoryFactory.lastCreated().busName()).isEqualTo("ReportBus");
    }

    @Test
    void createsTheReportQueue() throws JMSException {
        assertThat(((Queue) config.reportQueue(TestProfiles.virginiaProperties(Paths.get("tmp")))).getQueueName())
                .isEqualTo("REPORT.OUT.QUEUE");
    }

    @Test
    void failsFastWhenBusSettingsAreMissingOrUnresolved() {
        assertThatThrownBy(() -> config.sibConnectionFactory(properties("", "")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reporting.sib.bus-name");
        assertThatThrownBy(() -> config.sibConnectionFactory(properties("${SIB_BUS}", "")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SIB_BUS");
    }

    @Test
    void reportsThinClientErrorsWithTheirCause() {
        assertThatThrownBy(() -> config.sibConnectionFactory(properties(JmsFactoryFactory.FAILING_BUS, "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot use the IBM SIB thin client")
                .hasRootCauseMessage("CWSIA0241E: simulated thin-client failure");
    }

    @Test
    void neverPrintsThePassword() {
        assertThat(new ReportingProperties.Sib("Bus", "h:1:c", "chain", "user", "secret").toString())
                .doesNotContain("secret").contains("user");
    }

    private static ReportingProperties properties(String bus, String user) {
        ReportingProperties base = TestProfiles.virginiaProperties(Paths.get("tmp"));
        return new ReportingProperties(base.getNodeId(), ZoneId.of("America/New_York"), base.getDb(),
                base.getGeneration(), base.getTransmission(), base.getRetention(),
                new ReportingProperties.Sib(bus, "was-staging:7276:BootstrapBasicMessaging", "InboundBasicMessaging",
                        user, user.isEmpty() ? "" : "secret"),
                base.getFonts(), false, Map.of(), new ReportingProperties.Jobs(true, Duration.ofSeconds(30),
                        Duration.ofSeconds(60)), base.getDev());
    }
}
