package com.example.reporting;

/**
 * Generation failed for a reason that may go away, such as a full temp disk or an interrupted worker
 * (GENERATION_FAILED until the attempt limit).
 */
public final class TransientGenerationException extends ReportingException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message message without record values
     * @param cause underlying cause, may be {@code null}
     */
    public TransientGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public boolean isRetryable() {
        return true;
    }
}
