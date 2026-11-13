package com.example.reporting;

/**
 * The source data cannot be reported as-is, for example an unknown FIPS code or category (FR-20). The
 * document goes to DATA_ERROR and is never retried, because retrying cannot fix the data.
 */
public final class ReportDataException extends ReportingException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message clear message naming codes only, never record values
     */
    public ReportDataException(String message) {
        super(message, null);
    }

    @Override
    public boolean isRetryable() {
        return false;
    }
}
