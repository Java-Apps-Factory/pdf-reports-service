package com.example.reporting.profile;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated view of the {@code reporting.*} configuration (SPEC section 16).
 *
 * <p>Immutable and constructor-bound, so a wrong value stops startup. Groups are added by the phase that
 * first reads them; cross-checks against reference data are done by {@link ProfileValidator}.
 */
@ConfigurationProperties("reporting")
@ConstructorBinding
@Validated
public final class ReportingProperties {

    @NotBlank
    private final String nodeId;

    @NotNull
    private final ZoneId businessZone;

    @Valid
    @NotNull
    private final Db db;

    @Valid
    @NotNull
    private final Generation generation;

    @Valid
    @NotNull
    private final Transmission transmission;

    @Valid
    @NotNull
    private final Fonts fonts;

    private final boolean taggingEnabled;

    @Valid
    @NotNull
    private final Map<String, StateProfile> states;

    @Valid
    @NotNull
    private final Jobs jobs;

    @Valid
    @NotNull
    private final Dev dev;

    /**
     * Creates the properties; called by Spring Boot's binder.
     *
     * @param nodeId lease owner name, unique per node
     * @param businessZone business time zone used for schedules and printed timestamps
     * @param db database-specific settings
     * @param generation PDF generation settings
     * @param transmission transmission settings
     * @param fonts font resources
     * @param taggingEnabled whether PDFs are tagged for PDF/UA (Phase 5)
     * @param states presentation profile per state code
     * @param jobs background job settings
     * @param dev development-only settings
     */
    public ReportingProperties(
            String nodeId,
            ZoneId businessZone,
            @DefaultValue Db db,
            @DefaultValue Generation generation,
            @DefaultValue Transmission transmission,
            @DefaultValue Fonts fonts,
            @DefaultValue("false") boolean taggingEnabled,
            Map<String, StateProfile> states,
            @DefaultValue Jobs jobs,
            @DefaultValue Dev dev) {
        this.jobs = jobs;
        this.nodeId = nodeId;
        this.businessZone = businessZone;
        this.db = db;
        this.generation = generation;
        this.transmission = transmission;
        this.fonts = fonts;
        this.taggingEnabled = taggingEnabled;
        this.states = states == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(states));
        this.dev = dev;
    }

    public String getNodeId() {
        return nodeId;
    }

    public ZoneId getBusinessZone() {
        return businessZone;
    }

    public Db getDb() {
        return db;
    }

    public Generation getGeneration() {
        return generation;
    }

    public Transmission getTransmission() {
        return transmission;
    }

    public Fonts getFonts() {
        return fonts;
    }

    public boolean isTaggingEnabled() {
        return taggingEnabled;
    }

    /**
     * Returns the state profiles keyed by state code, sorted.
     *
     * @return unmodifiable map
     */
    public Map<String, StateProfile> getStates() {
        return Collections.unmodifiableMap(states);
    }

    public Jobs getJobs() {
        return jobs;
    }

    public Dev getDev() {
        return dev;
    }

    /** Database-specific settings. */
    public static final class Db {

        @NotBlank
        private final String nowQuery;

        /**
         * Creates the group.
         *
         * @param nowQuery vendor query that returns the database clock (AD-03)
         */
        public Db(@DefaultValue("SELECT CURRENT_TIMESTAMP") String nowQuery) {
            this.nowQuery = nowQuery;
        }

        public String getNowQuery() {
            return nowQuery;
        }
    }

    /** PDF generation settings. */
    public static final class Generation {

        @Min(1)
        private final int workers;

        @NotNull
        private final Duration pollInterval;

        @NotNull
        private final Duration leaseDuration;

        @Min(1)
        private final int maxAttempts;

        @Valid
        @NotNull
        private final BackoffSettings backoff;

        @Min(1)
        private final int fetchSize;

        @NotNull
        private final Path tempDir;

        /**
         * Creates the group.
         *
         * @param workers generation threads per node
         * @param pollInterval delay between generation polls
         * @param leaseDuration generation lease; must exceed 5 minutes (checked by {@link ProfileValidator})
         * @param maxAttempts attempts before GENERATION_FAILED_FINAL
         * @param backoff delay before a failed generation is retried
         * @param fetchSize JDBC fetch size of the report row stream
         * @param tempDir directory for PDFBox scratch files and finished PDFs
         */
        public Generation(
                @DefaultValue("4") int workers,
                @DefaultValue("5s") Duration pollInterval,
                @DefaultValue("15m") Duration leaseDuration,
                @DefaultValue("3") int maxAttempts,
                @DefaultValue BackoffSettings backoff,
                @DefaultValue("500") int fetchSize,
                @DefaultValue("${java.io.tmpdir}/fips-reports") Path tempDir) {
            this.workers = workers;
            this.pollInterval = pollInterval;
            this.maxAttempts = maxAttempts;
            this.backoff = backoff;
            this.leaseDuration = leaseDuration;
            this.fetchSize = fetchSize;
            this.tempDir = tempDir;
        }

        public int getWorkers() {
            return workers;
        }

        public Duration getPollInterval() {
            return pollInterval;
        }

        public Duration getLeaseDuration() {
            return leaseDuration;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public BackoffSettings getBackoff() {
            return backoff;
        }

        public int getFetchSize() {
            return fetchSize;
        }

        public Path getTempDir() {
            return tempDir;
        }
    }

    /** Exponential backoff: min(initial × multiplier^(attempt − 1), max), ±10% jitter (SPEC section 17). */
    public static final class BackoffSettings {

        @NotNull
        private final Duration initial;

        @DecimalMin("1.0")
        private final double multiplier;

        @NotNull
        private final Duration max;

        /**
         * Creates the group.
         *
         * @param initial delay after the first failure
         * @param multiplier growth factor per further failure
         * @param max upper bound of the delay
         */
        public BackoffSettings(
                @DefaultValue("1m") Duration initial,
                @DefaultValue("2.0") double multiplier,
                @DefaultValue("30m") Duration max) {
            this.initial = initial;
            this.multiplier = multiplier;
            this.max = max;
        }

        public Duration getInitial() {
            return initial;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public Duration getMax() {
            return max;
        }
    }

    /** Intervals of the singleton background jobs (SPEC section 15). */
    public static final class Jobs {

        private final boolean enabled;

        @NotNull
        private final Duration finalizerInterval;

        @NotNull
        private final Duration leaseSweepInterval;

        /**
         * Creates the group.
         *
         * @param enabled whether pollers and jobs are scheduled on this node
         * @param finalizerInterval delay between {@code RunFinalizer} passes
         * @param leaseSweepInterval delay between {@code LeaseRecoverySweeper} passes
         */
        public Jobs(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("30s") Duration finalizerInterval,
                @DefaultValue("60s") Duration leaseSweepInterval) {
            this.enabled = enabled;
            this.finalizerInterval = finalizerInterval;
            this.leaseSweepInterval = leaseSweepInterval;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Duration getFinalizerInterval() {
            return finalizerInterval;
        }

        public Duration getLeaseSweepInterval() {
            return leaseSweepInterval;
        }
    }

    /** Transmission settings. */
    public static final class Transmission {

        @NotBlank
        private final String queueName;

        /**
         * Creates the group.
         *
         * @param queueName destination queue for report messages
         */
        public Transmission(@DefaultValue("REPORT.OUT.QUEUE") String queueName) {
            this.queueName = queueName;
        }

        public String getQueueName() {
            return queueName;
        }
    }

    /** Font resources (Spring resource locations). */
    public static final class Fonts {

        @NotBlank
        private final String regular;

        @NotBlank
        private final String bold;

        @NotBlank
        private final String italic;

        /**
         * Creates the group.
         *
         * @param regular regular TrueType font
         * @param bold bold TrueType font
         * @param italic italic TrueType font
         */
        public Fonts(
                @DefaultValue("classpath:fonts/NotoSans-Regular.ttf") String regular,
                @DefaultValue("classpath:fonts/NotoSans-Bold.ttf") String bold,
                @DefaultValue("classpath:fonts/NotoSans-Italic.ttf") String italic) {
            this.regular = regular;
            this.bold = bold;
            this.italic = italic;
        }

        public String getRegular() {
            return regular;
        }

        public String getBold() {
            return bold;
        }

        public String getItalic() {
            return italic;
        }
    }

    /** Development-only settings; used by the {@code h2} profile. */
    public static final class Dev {

        @Min(0)
        private final int seedRecords;

        /**
         * Creates the group.
         *
         * @param seedRecords number of fake source records to insert into an empty RPT_RECORD
         */
        public Dev(@DefaultValue("0") int seedRecords) {
            this.seedRecords = seedRecords;
        }

        public int getSeedRecords() {
            return seedRecords;
        }
    }
}
