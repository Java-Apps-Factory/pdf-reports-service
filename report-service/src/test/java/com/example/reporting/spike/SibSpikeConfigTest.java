package com.example.reporting.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reporting.profile.ReportingProperties;
import com.ibm.websphere.sib.api.jms.JmsFactoryFactory;
import com.ibm.websphere.sib.api.jms.JmsFactoryFactory.RecordingFactory;
import java.time.ZoneId;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Queue;
import org.junit.jupiter.api.Test;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;

/** {@link SibSpikeConfig} against the test stand-in of the IBM thin-client API. */
class SibSpikeConfigTest {

    private final SibSpikeConfig config = new SibSpikeConfig();

    @Test
    void configuresTheThinClientFactoryFromProperties() {
        ConnectionFactory factory = config.sibSpikeConnectionFactory(properties("ReportBus", ""));

        assertThat(factory).isInstanceOf(RecordingFactory.class);
        RecordingFactory sib = (RecordingFactory) factory;
        assertThat(sib.busName()).isEqualTo("ReportBus");
        assertThat(sib.providerEndpoints()).isEqualTo("was-staging:7276:BootstrapBasicMessaging");
        assertThat(sib.targetTransportChain()).isEqualTo("InboundBasicMessaging");
    }

    @Test
    void wrapsTheFactoryWithCredentialsWhenAUserIsConfigured() {
        ConnectionFactory factory = config.sibSpikeConnectionFactory(properties("ReportBus", "svc-report"));

        assertThat(factory).isInstanceOf(UserCredentialsConnectionFactoryAdapter.class);
        assertThat(JmsFactoryFactory.lastCreated().busName()).isEqualTo("ReportBus");
    }

    @Test
    void createsTheQueueFromTheTransmissionQueueName() throws JMSException {
        Queue queue = config.sibSpikeQueue(new ReportingProperties("node-1", ZoneId.of("America/New_York"),
                new ReportingProperties.Db("SELECT CURRENT_TIMESTAMP"),
                new ReportingProperties.Transmission("REPORT.OUT.QUEUE"), new ReportingProperties.Dev(0)));
        assertThat(queue.getQueueName()).isEqualTo("REPORT.OUT.QUEUE");
    }

    @Test
    void failsFastWhenBusSettingsAreMissing() {
        assertThatThrownBy(() -> config.sibSpikeConnectionFactory(properties("", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reporting.sib.bus-name");
        assertThatThrownBy(() -> config.sibSpikeConnectionFactory(
                new SibSpikeProperties("ReportBus", null, "InboundBasicMessaging", "", "", 1024)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reporting.sib.provider-endpoints");
    }

    @Test
    void reportsThinClientErrorsWithTheirCause() {
        assertThatThrownBy(() -> config.sibSpikeConnectionFactory(properties(JmsFactoryFactory.FAILING_BUS, "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot use the IBM SIB thin client")
                .hasRootCauseMessage("CWSIA0241E: simulated thin-client failure");
    }

    private static SibSpikeProperties properties(String bus, String user) {
        return new SibSpikeProperties(bus, "was-staging:7276:BootstrapBasicMessaging", "InboundBasicMessaging",
                user, user.isEmpty() ? "" : "secret", 1024);
    }
}
