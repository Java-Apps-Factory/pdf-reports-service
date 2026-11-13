package com.example.reporting;

/**
 * Base of the service's unchecked exceptions (SPEC section 17). Each subtype is classified once, where it
 * happens, as retryable or not.
 *
 * <p>Messages never contain record values (NFR-13): only ids, counts, FIPS and category codes.
 */
public abstract class ReportingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message message without record values
     * @param cause underlying cause, may be {@code null}
     */
    protected ReportingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Tells whether the failed work may succeed when retried later.
     *
     * @return true for transient failures
     */
    public abstract boolean isRetryable();
}
