package com.example.reporting.transmission;

import com.example.reporting.ReportingException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSSecurityException;
import javax.jms.MessageFormatException;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.jms.JmsSecurityException;

/**
 * Classifies a send failure once (SPEC section 17): security, destination and message-format errors and
 * problems with the stored content are permanent; every other JMS or database error is transient.
 */
final class SendFailureClassifier {

    private SendFailureClassifier() {
    }

    static boolean isPermanent(Throwable failure) {
        if (failure instanceof ReportingException) {
            return !((ReportingException) failure).isRetryable();
        }
        if (failure instanceof NonTransientDataAccessException) {
            return true;
        }
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof JmsSecurityException
                    || t instanceof org.springframework.jms.InvalidDestinationException
                    || t instanceof org.springframework.jms.MessageFormatException
                    || t instanceof JMSSecurityException || t instanceof InvalidDestinationException
                    || t instanceof MessageFormatException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
