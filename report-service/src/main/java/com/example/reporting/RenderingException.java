package com.example.reporting;

/** Rendering the PDF failed for a reason a retry will not fix (GENERATION_FAILED_FINAL). */
public final class RenderingException extends ReportingException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message message without record values
     * @param cause underlying cause, may be {@code null}
     */
    public RenderingException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public boolean isRetryable() {
        return false;
    }
}
