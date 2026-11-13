package com.example.reporting.orchestration;

import com.example.reporting.tracking.TriggerType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Request to start a run: state, optional FIPS filter, trigger and requester. */
public final class StartRunCommand {

    private final String stateCode;
    private final List<String> fipsCodes;
    private final TriggerType triggerType;
    private final String requestedBy;

    private StartRunCommand(Builder b) {
        this.stateCode = Objects.requireNonNull(b.stateCode, "stateCode");
        this.fipsCodes = List.copyOf(b.fipsCodes);
        this.triggerType = Objects.requireNonNull(b.triggerType, "triggerType");
        this.requestedBy = Objects.requireNonNull(b.requestedBy, "requestedBy");
    }

    /**
     * Returns a new builder.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public String stateCode() {
        return stateCode;
    }

    /**
     * Returns the FIPS filter.
     *
     * @return codes; empty means all FIPS codes with records
     */
    public List<String> fipsCodes() {
        return fipsCodes;
    }

    public TriggerType triggerType() {
        return triggerType;
    }

    public String requestedBy() {
        return requestedBy;
    }

    /** Builder for {@link StartRunCommand}. */
    public static final class Builder {
        private String stateCode;
        private final List<String> fipsCodes = new ArrayList<>();
        private TriggerType triggerType;
        private String requestedBy;

        private Builder() {
        }

        /**
         * Sets the state code.
         *
         * @param value state code
         * @return this builder
         */
        public Builder stateCode(String value) {
            this.stateCode = value;
            return this;
        }

        /**
         * Sets the FIPS filter.
         *
         * @param values codes, may be {@code null} or empty for all
         * @return this builder
         */
        public Builder fipsCodes(List<String> values) {
            this.fipsCodes.clear();
            if (values != null) {
                this.fipsCodes.addAll(values);
            }
            return this;
        }

        /**
         * Sets the trigger.
         *
         * @param value trigger
         * @return this builder
         */
        public Builder triggerType(TriggerType value) {
            this.triggerType = value;
            return this;
        }

        /**
         * Sets the requester.
         *
         * @param value caller identity
         * @return this builder
         */
        public Builder requestedBy(String value) {
            this.requestedBy = value;
            return this;
        }

        /**
         * Builds the command.
         *
         * @return immutable command
         */
        public StartRunCommand build() {
            return new StartRunCommand(this);
        }
    }
}
