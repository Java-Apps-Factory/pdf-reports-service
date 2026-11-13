package com.example.reporting.transmission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.reporting.observability.ReportingMetrics;
import com.example.reporting.profile.TestProfiles;
import com.example.reporting.tracking.DbClock;
import com.example.reporting.tracking.DocumentClaimRepository;
import com.example.reporting.tracking.DocumentClaimRepository.Claim;
import com.example.reporting.tracking.DocumentStatus;
import com.example.reporting.tracking.ReportContentStore;
import com.example.reporting.tracking.ReportDocument;
import com.example.reporting.tracking.ReportDocumentRepository;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.ReportRunRepository;
import com.example.reporting.tracking.TriggerType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jms.JmsSecurityException;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.test.util.ReflectionTestUtils;

/** Stage C outcomes with mocked persistence and transmitter. */
class SendWorkerTest {

    private static final Instant NOW = Instant.parse("2026-11-10T19:05:00Z");
    private static final Claim CLAIM = new Claim(7L, 4);
    private static final byte[] PDF = "%PDF-1.7 sample".getBytes(StandardCharsets.US_ASCII);

    private final ReportDocumentRepository documents = mock(ReportDocumentRepository.class);
    private final ReportRunRepository runs = mock(ReportRunRepository.class);
    private final ReportContentStore content = mock(ReportContentStore.class);
    private final DocumentClaimRepository claims = mock(DocumentClaimRepository.class);
    private final ReportTransmitter transmitter = mock(ReportTransmitter.class);
    private final DbClock clock = mock(DbClock.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private SendWorker worker;
    private ReportDocument document;

    @BeforeEach
    void setUp() {
        worker = new SendWorker(documents, runs, content, claims, transmitter, clock,
                TestProfiles.virginiaProperties(Paths.get("tmp")), new ReportingMetrics(meters));
        ReportRun run = ReportRun.start("VA", TriggerType.API, "jdoe", null, Instant.parse("2026-11-10T07:00:00Z"));
        ReflectionTestUtils.setField(run, "runId", 3L);
        document = ReportDocument.create(3L, "VA", "51059", DocumentStatus.PENDING, null, NOW);
        ReflectionTestUtils.setField(document, "docId", 7L);
        ReflectionTestUtils.setField(document, "sha256", SendWorker.sha256(PDF));
        ReflectionTestUtils.setField(document, "recordCount", 1240);
        ReflectionTestUtils.setField(document, "pageCount", 31);
        ReflectionTestUtils.setField(document, "generatedAt", Instant.parse("2026-11-10T07:03:11.456Z"));
        attempts(1);
        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(runs.findById(3L)).thenReturn(Optional.of(run));
        when(content.load(7L)).thenReturn(Optional.of(PDF));
        when(clock.now()).thenReturn(NOW);
        when(claims.markSent(any(), anyString(), any())).thenReturn(true);
        when(claims.markSendEnded(any(), any(), any(), anyString(), any())).thenReturn(true);
        when(transmitter.send(any())).thenReturn("ID:42");
    }

    @Test
    void sendsTheStoredPdfWithTheContractPropertiesAndRecordsTheMessageId() throws JMSException {
        worker.send(CLAIM);

        verify(claims).markSent(CLAIM, "ID:42", NOW);
        ArgumentCaptor<ReportMessage> message = ArgumentCaptor.forClass(ReportMessage.class);
        verify(transmitter).send(message.capture());
        BytesMessage jms = mock(BytesMessage.class);
        message.getValue().writeTo(jms);
        verify(jms).writeBytes(PDF);
        verify(jms).setLongProperty(ReportMessageProperties.REPORT_DOC_ID, 7L);
        verify(jms).setLongProperty(ReportMessageProperties.RUN_ID, 3L);
        verify(jms).setStringProperty(ReportMessageProperties.STATE_CODE, "VA");
        verify(jms).setStringProperty(ReportMessageProperties.FIPS_CODE, "51059");
        verify(jms).setStringProperty(ReportMessageProperties.GENERATED_AT, "2026-11-10T02:03:11-05:00");
        verify(jms).setStringProperty(ReportMessageProperties.DATA_AS_OF, "2026-11-10T02:00:00-05:00");
        verify(jms).setIntProperty(ReportMessageProperties.RECORD_COUNT, 1240);
        verify(jms).setIntProperty(ReportMessageProperties.PAGE_COUNT, 31);
        verify(jms).setStringProperty(ReportMessageProperties.SHA256, SendWorker.sha256(PDF));
        verify(jms).setStringProperty(ReportMessageProperties.CONTENT_TYPE, "application/pdf");
        verify(jms).setIntProperty(ReportMessageProperties.SCHEMA_VERSION, 1);
        assertThat(message.getValue().size()).isEqualTo(PDF.length);
        assertThat(meters.get("reporting.send.duration").tag("outcome", "sent").timer().count()).isEqualTo(1);
    }

    @Test
    void aNewerDocumentSupersedesThisOneAndNothingIsSent() {
        when(claims.newerDocumentExists(7L)).thenReturn(true);

        worker.send(CLAIM);

        verify(claims).markSendEnded(eq(CLAIM), eq(DocumentStatus.SUPERSEDED), isNull(), anyString(), eq(NOW));
        verify(transmitter, never()).send(any());
        verify(content, never()).load(anyLong());
    }

    @Test
    void missingPurgedOrCorruptContentIsAPermanentFailure() {
        when(content.load(7L)).thenReturn(Optional.empty());
        worker.send(CLAIM);
        verify(claims).markSendEnded(eq(CLAIM), eq(DocumentStatus.SEND_FAILED_FINAL), isNull(),
                eq("PermanentTransmissionException: Stored PDF is missing"), eq(NOW));

        when(content.load(7L)).thenReturn(Optional.of("tampered".getBytes(StandardCharsets.US_ASCII)));
        worker.send(CLAIM);
        verify(claims).markSendEnded(eq(CLAIM), eq(DocumentStatus.SEND_FAILED_FINAL), isNull(),
                eq("PermanentTransmissionException: Stored PDF does not match its SHA-256"), eq(NOW));

        ReflectionTestUtils.setField(document, "contentPurgedAt", NOW);
        worker.send(CLAIM);
        verify(claims).markSendEnded(eq(CLAIM), eq(DocumentStatus.SEND_FAILED_FINAL), isNull(),
                eq("PermanentTransmissionException: Stored PDF was purged"), eq(NOW));
        verify(transmitter, never()).send(any());
    }

    @Test
    void transientErrorsAreRetriedInProcessBeforeTheAttemptFails() {
        when(transmitter.send(any())).thenThrow(new UncategorizedJmsException("broker down"))
                .thenReturn("ID:43");

        worker.send(CLAIM);

        verify(transmitter, times(2)).send(any());
        verify(claims).markSent(CLAIM, "ID:43", NOW);
    }

    @Test
    void anAttemptThatKeepsFailingIsRetriedLaterWithBackoff() {
        when(transmitter.send(any())).thenThrow(new UncategorizedJmsException("broker down"));
        attempts(2);

        worker.send(CLAIM);

        verify(transmitter, times(3)).send(any());
        ArgumentCaptor<Instant> next = ArgumentCaptor.forClass(Instant.class);
        verify(claims).markSendEnded(eq(CLAIM), eq(DocumentStatus.SEND_FAILED), next.capture(),
                eq("UncategorizedJmsException: broker down"), eq(NOW));
        // Second attempt: 1 min × 2 = 2 min, ±10% jitter.
        assertThat(Duration.between(NOW, next.getValue())).isBetween(Duration.ofSeconds(108),
                Duration.ofSeconds(132));
    }

    @Test
    void theEighthFailedAttemptIsFinal() {
        when(transmitter.send(any())).thenThrow(new UncategorizedJmsException("broker down"));
        attempts(8);

        worker.send(CLAIM);

        verify(claims).markSendEnded(eq(CLAIM), eq(DocumentStatus.SEND_FAILED_FINAL), isNull(), anyString(),
                eq(NOW));
    }

    @Test
    void permanentJmsErrorsAreNotRetried() {
        when(transmitter.send(any())).thenThrow(new JmsSecurityException(new JMSSecurityException("no")));

        worker.send(CLAIM);

        verify(transmitter, times(1)).send(any());
        verify(claims).markSendEnded(eq(CLAIM), eq(DocumentStatus.SEND_FAILED_FINAL), isNull(), anyString(),
                eq(NOW));
        assertThat(meters.counter("reporting.send.failures", "state", "VA", "classification", "permanent").count())
                .isEqualTo(1);
        assertThat(meters.get("reporting.send.duration").tag("outcome", "failed").timer().count()).isEqualTo(1);
    }

    @Test
    void aLostLeaseAfterSendingIsOnlyLoggedAndRecordingFailuresAreLeftToRecovery() {
        when(claims.markSent(any(), anyString(), any())).thenReturn(false);
        worker.send(CLAIM);
        verify(claims, never()).markSendEnded(any(), any(), any(), anyString(), any());

        when(documents.findById(7L)).thenReturn(Optional.empty());
        when(claims.markSendEnded(any(), any(), any(), anyString(), any()))
                .thenThrow(new IllegalStateException("database down"));
        worker.send(CLAIM);
        verify(claims).markSendEnded(eq(CLAIM), eq(DocumentStatus.SEND_FAILED_FINAL), isNull(), anyString(),
                eq(NOW));
    }

    private void attempts(int value) {
        ReflectionTestUtils.setField(document, "sendAttempts", (short) value);
    }
}
