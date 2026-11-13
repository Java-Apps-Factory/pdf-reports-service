package com.example.reporting.api;

/** Exceptions raised by the controllers and mapped by {@link ApiExceptionHandler}. */
final class ApiExceptions {

    private ApiExceptions() {
    }

    /** A run or document does not exist (404). */
    static final class NotFound extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NotFound(String message) {
            super(message);
        }
    }

    /** The stored PDF was purged (410). */
    static final class ContentPurged extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ContentPurged(String message) {
            super(message);
        }
    }
}
