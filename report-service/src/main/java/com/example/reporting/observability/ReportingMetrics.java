package com.example.reporting.observability;

import com.example.reporting.tracking.DocumentStatus;
import com.example.reporting.tracking.TriggerType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * The service's Micrometer meters (SPEC section 18). Tags carry only state codes, statuses and outcomes, never
 * record values (NFR-13).
 */
@Component
public class ReportingMetrics {

    private final MeterRegistry registry;
    private final Map<DocumentStatus, AtomicLong> documentsByStatus = new EnumMap<>(DocumentStatus.class);

    /**
     * Creates the meters; the per-status gauges start at 0.
     *
     * @param registry the application meter registry
     */
    public ReportingMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (DocumentStatus status : DocumentStatus.values()) {
            AtomicLong value = new AtomicLong();
            documentsByStatus.put(status, value);
            Gauge.builder("reporting.documents.by.status", value, AtomicLong::get)
                    .tag("status", status.name())
                    .description("Documents per status across the cluster")
                    .register(registry);
        }
    }

    /**
     * Counts a created run.
     *
     * @param state state code
     * @param trigger trigger
     */
    public void runStarted(String state, TriggerType trigger) {
        registry.counter("reporting.runs.started", "state", state, "trigger", trigger.name()).increment();
    }

    /**
     * Records a stored PDF.
     *
     * @param state state code
     * @param took time to render and store
     * @param pages pages
     * @param bytes size
     */
    public void documentGenerated(String state, Duration took, int pages, long bytes) {
        generated(state, "success").increment();
        Timer.builder("reporting.generation.duration").tag("state", state).register(registry).record(took);
        DistributionSummary.builder("reporting.pdf.pages").tag("state", state).register(registry).record(pages);
        DistributionSummary.builder("reporting.pdf.bytes").tag("state", state).baseUnit("bytes").register(registry)
                .record(bytes);
    }

    /**
     * Records a failed generation attempt.
     *
     * @param state state code
     * @param dataError true for DATA_ERROR, false for other failures
     */
    public void generationFailed(String state, boolean dataError) {
        generated(state, dataError ? "data_error" : "failed").increment();
    }

    /**
     * Records one send attempt.
     *
     * @param state state code
     * @param outcome {@code sent}, {@code superseded} or {@code failed}
     * @param took time spent
     */
    public void sendFinished(String state, String outcome, Duration took) {
        Timer.builder("reporting.send.duration").tags("state", state, "outcome", outcome).register(registry)
                .record(took);
    }

    /**
     * Counts a failed send attempt.
     *
     * @param state state code
     * @param permanent true for permanent, false for transient
     */
    public void sendFailed(String state, boolean permanent) {
        registry.counter("reporting.send.failures", "state", state, "classification",
                permanent ? "permanent" : "transient").increment();
    }

    /**
     * Counts a recovered expired lease.
     *
     * @param kind {@code generation} or {@code send}
     */
    public void leaseRecovered(String kind) {
        registry.counter("reporting.leases.recovered", "kind", kind).increment();
    }

    /**
     * Sets the per-status gauges; statuses missing from {@code counts} become 0.
     *
     * @param counts documents per status across the cluster
     */
    public void documentsByStatus(Map<DocumentStatus, Long> counts) {
        documentsByStatus.forEach((status, value) -> value.set(counts.getOrDefault(status, 0L)));
    }

    private Counter generated(String state, String outcome) {
        return registry.counter("reporting.documents.generated", "state", state, "outcome", outcome);
    }
}
