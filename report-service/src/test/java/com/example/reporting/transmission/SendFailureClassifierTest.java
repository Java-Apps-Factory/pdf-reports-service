package com.example.reporting.transmission;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reporting.PermanentTransmissionException;
import com.example.reporting.TransientTransmissionException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.jms.MessageFormatException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jms.JmsSecurityException;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.support.JmsUtils;

/** Send classification of SPEC section 17. */
class SendFailureClassifierTest {

    @Test
    void securityDestinationAndFormatErrorsArePermanent() {
        assertThat(SendFailureClassifier.isPermanent(new JmsSecurityException(new JMSSecurityException("denied"))))
                .isTrue();
        assertThat(SendFailureClassifier.isPermanent(JmsUtils.convertJmsAccessException(
                new InvalidDestinationException("no queue")))).isTrue();
        assertThat(SendFailureClassifier.isPermanent(JmsUtils.convertJmsAccessException(
                new MessageFormatException("bad")))).isTrue();
        assertThat(SendFailureClassifier.isPermanent(new UncategorizedJmsException(
                new JMSException("wrapped", "x")) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public synchronized Throwable getCause() {
                        return new JMSSecurityException("inner");
                    }
                })).isTrue();
    }

    @Test
    void otherJmsErrorsAreTransient() {
        assertThat(SendFailureClassifier.isPermanent(new UncategorizedJmsException(new JMSException("down"))))
                .isFalse();
        assertThat(SendFailureClassifier.isPermanent(JmsUtils.convertJmsAccessException(new JMSException("x"))))
                .isFalse();
    }

    @Test
    void contentProblemsArePermanentAndExplicitTransientsAreNot() {
        assertThat(SendFailureClassifier.isPermanent(new PermanentTransmissionException("missing", null))).isTrue();
        assertThat(SendFailureClassifier.isPermanent(new TransientTransmissionException("later", null))).isFalse();
    }

    @Test
    void databaseErrorsFollowTheirOwnClass() {
        assertThat(SendFailureClassifier.isPermanent(new QueryTimeoutException("slow"))).isFalse();
        assertThat(SendFailureClassifier.isPermanent(new DataIntegrityViolationException("bad"))).isTrue();
        assertThat(SendFailureClassifier.isPermanent(new IllegalStateException("?"))).isFalse();
        assertThat(SendFailureClassifier.describe(new IllegalStateException("boom")))
                .isEqualTo("IllegalStateException: boom");
    }
}
