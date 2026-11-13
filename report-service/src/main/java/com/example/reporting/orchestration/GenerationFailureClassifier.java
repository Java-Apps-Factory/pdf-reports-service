package com.example.reporting.orchestration;

import com.example.reporting.ReportDataException;
import com.example.reporting.ReportingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

/**
 * Classifies a generation failure once, where it happens (SPEC section 17): data problems end in DATA_ERROR,
 * transient problems are retried until the attempt limit, everything else is final.
 */
final class GenerationFailureClassifier {

    /** What to do with a failed generation. */
    enum Outcome {
        /** DATA_ERROR, never retried. */
        DATA_ERROR,
        /** GENERATION_FAILED with backoff, or FINAL when attempts are used up. */
        RETRY,
        /** GENERATION_FAILED_FINAL. */
        FINAL
    }

    private GenerationFailureClassifier() {
    }

    static Outcome classify(Throwable failure) {
        if (failure instanceof ReportDataException) {
            return Outcome.DATA_ERROR;
        }
        if (failure instanceof ReportingException) {
            return ((ReportingException) failure).isRetryable() ? Outcome.RETRY : Outcome.FINAL;
        }
        if (failure instanceof TransientDataAccessException || failure instanceof RecoverableDataAccessException
                || failure instanceof CannotGetJdbcConnectionException) {
            return Outcome.RETRY;
        }
        if (failure instanceof NonTransientDataAccessException) {
            return Outcome.FINAL;
        }
        if (failure instanceof IOException || failure instanceof UncheckedIOException) {
            // Temp-file I/O outside the renderer, e.g. a missing or full temp directory.
            return Outcome.RETRY;
        }
        // Unexpected errors are retried within the attempt limit rather than failing the document at once.
        return Outcome.RETRY;
    }

    static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
