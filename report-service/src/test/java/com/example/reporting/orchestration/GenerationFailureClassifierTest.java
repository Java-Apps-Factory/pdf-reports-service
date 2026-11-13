package com.example.reporting.orchestration;

import static com.example.reporting.orchestration.GenerationFailureClassifier.Outcome.DATA_ERROR;
import static com.example.reporting.orchestration.GenerationFailureClassifier.Outcome.FINAL;
import static com.example.reporting.orchestration.GenerationFailureClassifier.Outcome.RETRY;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.reporting.RenderingException;
import com.example.reporting.ReportDataException;
import com.example.reporting.TransientGenerationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

/** Classification table of SPEC section 17 for generation. */
class GenerationFailureClassifierTest {

    @Test
    void dataProblemsAreDataErrors() {
        assertThat(GenerationFailureClassifier.classify(new ReportDataException("unknown category")))
                .isEqualTo(DATA_ERROR);
    }

    @Test
    void renderingFollowsTheExceptionsOwnClassification() {
        assertThat(GenerationFailureClassifier.classify(new RenderingException("bad", null))).isEqualTo(FINAL);
        assertThat(GenerationFailureClassifier.classify(new TransientGenerationException("disk", null)))
                .isEqualTo(RETRY);
    }

    @Test
    void transientDatabaseErrorsAreRetried() {
        assertThat(GenerationFailureClassifier.classify(new QueryTimeoutException("slow"))).isEqualTo(RETRY);
        assertThat(GenerationFailureClassifier.classify(new TransientDataAccessResourceException("x")))
                .isEqualTo(RETRY);
        assertThat(GenerationFailureClassifier.classify(new RecoverableDataAccessException("x"))).isEqualTo(RETRY);
        assertThat(GenerationFailureClassifier.classify(new CannotGetJdbcConnectionException("pool")))
                .isEqualTo(RETRY);
    }

    @Test
    void nonTransientDatabaseErrorsAreFinal() {
        assertThat(GenerationFailureClassifier.classify(new BadSqlGrammarException("q", "SELECT", new SQLException())))
                .isEqualTo(FINAL);
        assertThat(GenerationFailureClassifier.classify(new DataIntegrityViolationException("dup"))).isEqualTo(FINAL);
    }

    @Test
    void tempFileProblemsAndUnexpectedErrorsAreRetried() {
        assertThat(GenerationFailureClassifier.classify(new IOException("tmp"))).isEqualTo(RETRY);
        assertThat(GenerationFailureClassifier.classify(new UncheckedIOException(new IOException("tmp"))))
                .isEqualTo(RETRY);
        assertThat(GenerationFailureClassifier.classify(new IllegalStateException("?"))).isEqualTo(RETRY);
    }

    @Test
    void describesWithClassAndMessage() {
        assertThat(GenerationFailureClassifier.describe(new IllegalStateException("boom")))
                .isEqualTo("IllegalStateException: boom");
        assertThat(GenerationFailureClassifier.describe(new NullPointerException())).isEqualTo("NullPointerException");
    }
}
