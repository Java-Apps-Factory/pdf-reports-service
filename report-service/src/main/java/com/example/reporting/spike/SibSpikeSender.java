package com.example.reporting.spike;

import com.example.reporting.transmission.ReportMessageProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * Sends one sample report message exactly as the production sender will (SPEC section 13):
 * a persistent {@code BytesMessage} in a transacted session with every contract property set.
 *
 * <p>Uses JMS 1.1 calls only, because the SIB thin client is a JMS 1.1 provider. Throwaway code
 * for the Phase 0 spike; Phase 4 implements {@code JmsReportTransmitter}.
 */
public final class SibSpikeSender {

    static final long SAMPLE_DOC_ID = 48213L;
    static final long SAMPLE_RUN_ID = 1702L;
    static final String SAMPLE_STATE = "VA";
    static final String SAMPLE_FIPS = "51059";
    static final int SAMPLE_RECORD_COUNT = 1240;
    static final int SAMPLE_PAGE_COUNT = 31;

    private static final byte[] PDF_MAGIC = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final ConnectionFactory connectionFactory;
    private final Destination destination;

    /**
     * Creates the sender.
     *
     * @param connectionFactory broker connection factory
     * @param destination queue to send to
     */
    public SibSpikeSender(ConnectionFactory connectionFactory, Destination destination) {
        this.connectionFactory = connectionFactory;
        this.destination = destination;
    }

    /**
     * Builds a filler payload of the given size that starts with a PDF signature.
     *
     * @param size payload size in bytes
     * @return the payload
     */
    public static byte[] samplePayload(int size) {
        if (size < PDF_MAGIC.length) {
            throw new IllegalArgumentException("Payload must be at least " + PDF_MAGIC.length + " bytes");
        }
        byte[] payload = new byte[size];
        System.arraycopy(PDF_MAGIC, 0, payload, 0, PDF_MAGIC.length);
        for (int i = PDF_MAGIC.length; i < size; i++) {
            payload[i] = (byte) ('A' + i % 26);
        }
        return payload;
    }

    /**
     * Sends the payload and commits the session.
     *
     * @param payload message body
     * @param now timestamp used for the {@code generatedAt} and {@code dataAsOf} properties
     * @return the JMS message id assigned by the broker
     * @throws JMSException if the broker rejects the connection or the message
     */
    public String send(byte[] payload, OffsetDateTime now) throws JMSException {
        Connection connection = connectionFactory.createConnection();
        try {
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageProducer producer = session.createProducer(destination);
            BytesMessage message = session.createBytesMessage();
            message.writeBytes(payload);
            setContractProperties(message, payload, now);
            producer.send(message, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
            session.commit();
            return message.getJMSMessageID();
        } finally {
            connection.close();
        }
    }

    private static void setContractProperties(Message message, byte[] payload, OffsetDateTime now)
            throws JMSException {
        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        message.setLongProperty(ReportMessageProperties.REPORT_DOC_ID, SAMPLE_DOC_ID);
        message.setLongProperty(ReportMessageProperties.RUN_ID, SAMPLE_RUN_ID);
        message.setStringProperty(ReportMessageProperties.STATE_CODE, SAMPLE_STATE);
        message.setStringProperty(ReportMessageProperties.FIPS_CODE, SAMPLE_FIPS);
        message.setStringProperty(ReportMessageProperties.GENERATED_AT, timestamp);
        message.setStringProperty(ReportMessageProperties.DATA_AS_OF, timestamp);
        message.setIntProperty(ReportMessageProperties.RECORD_COUNT, SAMPLE_RECORD_COUNT);
        message.setIntProperty(ReportMessageProperties.PAGE_COUNT, SAMPLE_PAGE_COUNT);
        message.setStringProperty(ReportMessageProperties.SHA256, sha256Hex(payload));
        message.setStringProperty(ReportMessageProperties.CONTENT_TYPE, ReportMessageProperties.CONTENT_TYPE_PDF);
        message.setIntProperty(ReportMessageProperties.SCHEMA_VERSION,
                ReportMessageProperties.CURRENT_SCHEMA_VERSION);
    }

    static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }
}
