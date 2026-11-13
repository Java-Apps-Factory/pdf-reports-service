package com.example.reporting.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reporting.tracking.DocumentStatus;
import com.example.reporting.tracking.TriggerType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The meters of SPEC section 18, with their names and tags. */
class ReportingMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ReportingMetrics metrics = new ReportingMetrics(registry);

    @Test
    void recordsEveryMeterWithItsTags() {
        metrics.runStarted("VA", TriggerType.SCHEDULED);
        metrics.documentGenerated("VA", Duration.ofMillis(250), 31, 422_325);
        metrics.generationFailed("VA", true);
        metrics.generationFailed("VA", false);
        metrics.sendFinished("VA", "sent", Duration.ofMillis(40));
        metrics.sendFailed("VA", false);
        metrics.leaseRecovered("generation");

        assertThat(registry.get("reporting.runs.started").tags("state", "VA", "trigger", "SCHEDULED").counter()
                .count()).isEqualTo(1);
        for (String outcome : new String[] {"success", "data_error", "failed"}) {
            assertThat(registry.get("reporting.documents.generated").tags("state", "VA", "outcome", outcome)
                    .counter().count()).isEqualTo(1);
        }
        assertThat(registry.get("reporting.generation.duration").tag("state", "VA").timer().count()).isEqualTo(1);
        assertThat(registry.get("reporting.pdf.pages").summary().totalAmount()).isEqualTo(31);
        assertThat(registry.get("reporting.pdf.bytes").summary().totalAmount()).isEqualTo(422_325);
        assertThat(registry.get("reporting.send.duration").tags("state", "VA", "outcome", "sent").timer().count())
                .isEqualTo(1);
        assertThat(registry.get("reporting.send.failures").tags("classification", "transient").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("reporting.leases.recovered").tag("kind", "generation").counter().count())
                .isEqualTo(1);
    }

    @Test
    void statusGaugesFollowTheLatestCountsAndDefaultToZero() {
        metrics.documentsByStatus(Map.of(DocumentStatus.SENT, 130L, DocumentStatus.DATA_ERROR, 3L));
        assertThat(gauge("SENT")).isEqualTo(130);
        assertThat(gauge("DATA_ERROR")).isEqualTo(3);
        assertThat(gauge("PENDING")).isZero();

        metrics.documentsByStatus(Map.of(DocumentStatus.SENT, 133L));
        assertThat(gauge("SENT")).isEqualTo(133);
        assertThat(gauge("DATA_ERROR")).isZero();
        assertThat(registry.get("reporting.documents.by.status").gauges()).hasSize(DocumentStatus.values().length);
    }

    private double gauge(String status) {
        return registry.get("reporting.documents.by.status").tag("status", status).gauge().value();
    }
}
