package com.example.reporting.profile;

import java.time.ZoneId;
import javax.validation.Valid;
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
 * <p>Immutable and constructor-bound, so a wrong value stops startup. Phase 0 binds only the
 * settings it uses; later phases add their own groups (generation, retention, fonts, states).
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
    private final Transmission transmission;

    @Valid
    @NotNull
    private final Dev dev;

    /**
     * Creates the properties; called by Spring Boot's binder.
     *
     * @param nodeId lease owner name, unique per node
     * @param businessZone business time zone used for schedules and printed timestamps
     * @param db database-specific settings
     * @param transmission transmission settings
     * @param dev development-only settings
     */
    public ReportingProperties(
            String nodeId,
            ZoneId businessZone,
            @DefaultValue Db db,
            @DefaultValue Transmission transmission,
            @DefaultValue Dev dev) {
        this.nodeId = nodeId;
        this.businessZone = businessZone;
        this.db = db;
        this.transmission = transmission;
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

    public Transmission getTransmission() {
        return transmission;
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
