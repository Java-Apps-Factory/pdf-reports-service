package com.example.reporting;

/** Sending failed for a reason that may go away, such as a broker outage (SEND_FAILED until the limit). */
public final class TransientTransmissionException extends ReportingException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message message without record values
     * @param cause underlying cause, may be {@code null}
     */
    public TransientTransmissionException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public boolean isRetryable() {
        return true;
    }
}
