package com.example.reporting;

/**
 * Sending cannot succeed by retrying: content missing, purged or failing its SHA-256 check, or a security,
 * destination or message-format error (SEND_FAILED_FINAL).
 */
public final class PermanentTransmissionException extends ReportingException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message message without record values
     * @param cause underlying cause, may be {@code null}
     */
    public PermanentTransmissionException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public boolean isRetryable() {
        return false;
    }
}
