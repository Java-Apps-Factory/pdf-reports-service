package com.example.reporting.orchestration;

/** The state is not active in RPT_STATE or has no profile; the API answers 404. */
public final class UnknownStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message message naming the state code
     */
    public UnknownStateException(String message) {
        super(message);
    }
}
