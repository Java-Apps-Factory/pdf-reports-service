package com.example.reporting.orchestration;

/** The run request is invalid, for example a FIPS code of another state; the API answers 400. */
public final class InvalidRunRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message message naming the offending codes
     */
    public InvalidRunRequestException(String message) {
        super(message);
    }
}
