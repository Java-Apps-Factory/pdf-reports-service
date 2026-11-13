package com.example.reporting.orchestration;

import java.util.Optional;

/** Another run of the state is still in its generation phase (FR-11); the API answers 409. */
public final class RunAlreadyActiveException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String stateCode;
    private final Long activeRunId;

    /**
     * Creates the exception.
     *
     * @param stateCode state code
     * @param activeRunId the active run, if known
     */
    public RunAlreadyActiveException(String stateCode, Optional<Long> activeRunId) {
        super("State " + stateCode + " already has "
                + activeRunId.map(id -> "run " + id).orElse("a run") + " in progress.");
        this.stateCode = stateCode;
        this.activeRunId = activeRunId.orElse(null);
    }

    public String getStateCode() {
        return stateCode;
    }

    /**
     * Returns the active run.
     *
     * @return run ID, or empty if it finished meanwhile
     */
    public Optional<Long> getActiveRunId() {
        return Optional.ofNullable(activeRunId);
    }
}
