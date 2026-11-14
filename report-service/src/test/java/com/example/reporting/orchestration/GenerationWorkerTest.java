package com.example.reporting.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.reporting.data.FipsWorkload;
import com.example.reporting.data.ReferenceDataRepository;
import com.example.reporting.data.ReportCategory;
import com.example.reporting.data.ReportDataProvider;
import com.example.reporting.data.ReportRow;
import com.example.reporting.observability.ReportingMetrics;
import com.example.reporting.profile.ProfileRegistry;
import com.example.reporting.profile.ProfileValidator;
import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.profile.TestProfiles;
import com.example.reporting.rendering.DocumentSpecFactory;
import com.example.reporting.rendering.FipsReportComposer;
import com.example.reporting.tracking.DbClock;
import com.example.reporting.tracking.DocumentClaimRepository;
import com.example.reporting.tracking.DocumentClaimRepository.Claim;
import com.example.reporting.tracking.DocumentClaimRepository.GeneratedFacts;
import com.example.reporting.tracking.DocumentStatus;
import com.example.reporting.tracking.ReportContentStore;
import com.example.reporting.tracking.ReportDocument;
import com.example.reporting.tracking.ReportDocumentRepository;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.ReportRunRepository;
import com.example.reporting.tracking.Sha256;
import com.example.reporting.tracking.TriggerType;
import com.example.reporting.transmission.DocumentGeneratedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Stage B outcomes with a real composer over in-memory rows and mocked persistence. */
class GenerationWorkerTest {

    private static final Instant NOW = Instant.parse("2026-11-10T19:05:00Z");
    private static final Claim CLAIM = new Claim(7L, 2);

    @TempDir
    Path tempDir;

    private final ReportDocumentRepository documents = mock(ReportDocumentRepository.class);
    private final ReportRunRepository runs = mock(ReportRunRepository.class);
    private final ReferenceDataRepository referenceData = mock(ReferenceDataRepository.class);
    private final ReportContentStore content = mock(ReportContentStore.class);
    private final DocumentClaimRepository claims = mock(DocumentClaimRepository.class);
    private final DbClock clock = mock(DbClock.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final List<ReportRow> rows = new ArrayList<>();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private GenerationWorker worker;
    private ReportDocument document;

    @BeforeEach
    void setUp() {
        ReportingProperties properties = TestProfiles.virginiaProperties(tempDir);
        DefaultResourceLoader loader = new DefaultResourceLoader();
        ProfileRegistry registry = new ProfileRegistry(properties, mock(ProfileValidator.class), loader);
        ReportDataProvider provider = new ReportDataProvider() {
            @Override
            public List<FipsWorkload> workload(String stateCode) {
                return List.of();
            }

            @Override
            public long streamRows(String stateCode, String fipsCode, Consumer<ReportRow> consumer) {
                rows.forEach(consumer);
                return rows.size();
            }
        };
        FipsReportComposer composer = new FipsReportComposer(registry, referenceData, provider,
                new DocumentSpecFactory(properties, loader), properties);
        worker = new GenerationWorker(documents, runs, composer, content, claims, clock, transactions, events,
                properties, new ReportingMetrics(meters));

        ReportRun run = ReportRun.start("VA", TriggerType.API, "jdoe", null, NOW);
        ReflectionTestUtils.setField(run, "runId", 3L);
        document = ReportDocument.create(3L, "VA", "51059", DocumentStatus.PENDING, null, NOW);
        ReflectionTestUtils.setField(document, "docId", 7L);
        attempts(1);
        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(runs.findById(3L)).thenReturn(Optional.of(run));
        when(referenceData.localityName("VA", "51059")).thenReturn(Optional.of("Fairfax County"));
        when(referenceData.activeCategories("VA")).thenReturn(List.of(new ReportCategory("CAT1", "Fees", 1)));
        when(clock.now()).thenReturn(NOW);
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(claims.markGenerated(any(), any(), any())).thenReturn(true);
        when(claims.markGenerationFailed(any(), any(), any(), anyString(), any())).thenReturn(true);
    }

    @Test
    void storesThePdfMarksItGeneratedAndPublishesTheEvent() throws Exception {
        rows.add(row("CAT1", 1, "VA-1"));
        rows.add(row("CAT1", 1, "VA-2"));

        worker.generate(CLAIM);

        ArgumentCaptor<GeneratedFacts> facts = ArgumentCaptor.forClass(GeneratedFacts.class);
        verify(claims).markGenerated(eq(CLAIM), facts.capture(), eq(NOW));
        assertThat(facts.getValue().recordCount()).isEqualTo(2);
        assertThat(facts.getValue().pageCount()).isEqualTo(1);
        assertThat(facts.getValue().sizeBytes()).isPositive();
        assertThat(facts.getValue().sha256()).matches("[0-9a-f]{64}");
        verify(content).store(eq(7L), any(Path.class));
        ArgumentCaptor<DocumentGeneratedEvent> event = ArgumentCaptor.forClass(DocumentGeneratedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().docId()).isEqualTo(7L);
        assertThat(event.getValue().runId()).isEqualTo(3L);
        assertThat(event.getValue().stateCode()).isEqualTo("VA");
        verify(transactions).commit(any());
        assertThat(tempFiles()).isEmpty();
        assertThat(meters.counter("reporting.documents.generated", "state", "VA", "outcome", "success").count())
                .isEqualTo(1);
        assertThat(meters.get("reporting.pdf.pages").summary().totalAmount()).isEqualTo(1);
        assertThat(meters.get("reporting.generation.duration").timer().count()).isEqualTo(1);
    }

    @Test
    void aLostLeaseRollsBackAndPublishesNothing() throws Exception {
        when(claims.markGenerated(any(), any(), any())).thenReturn(false);

        worker.generate(CLAIM);

        verify(events, never()).publishEvent(any());
        // TransactionTemplate hands a rollback-only status to commit(); the real manager then rolls back.
        ArgumentCaptor<TransactionStatus> status = ArgumentCaptor.forClass(TransactionStatus.class);
        verify(transactions).commit(status.capture());
        assertThat(status.getValue().isRollbackOnly()).isTrue();
        assertThat(tempFiles()).isEmpty();
    }

    @Test
    void unknownCategoryEndsInDataError() {
        rows.add(row("CATX", null, "VA-1"));

        worker.generate(CLAIM);

        verify(claims).markGenerationFailed(eq(CLAIM), eq(DocumentStatus.DATA_ERROR), isNull(),
                eq("ReportDataException: Record with category CATX that is not an active category of state VA"),
                eq(NOW));
        assertThat(meters.counter("reporting.documents.generated", "state", "VA", "outcome", "data_error").count())
                .isEqualTo(1);
    }

    @Test
    void transientFailureWithAttemptsLeftIsRetriedWithBackoff() {
        rows.add(row("CAT1", 1, "VA-1"));
        Thread.currentThread().interrupt();
        try {
            worker.generate(CLAIM);
        } finally {
            Thread.interrupted();
        }

        ArgumentCaptor<Instant> next = ArgumentCaptor.forClass(Instant.class);
        verify(claims).markGenerationFailed(eq(CLAIM), eq(DocumentStatus.GENERATION_FAILED), next.capture(),
                anyString(), eq(NOW));
        assertThat(Duration.between(NOW, next.getValue())).isBetween(Duration.ofSeconds(54), Duration.ofSeconds(66));
    }

    @Test
    void transientFailureOnTheLastAttemptIsFinal() {
        attempts(3);
        rows.add(row("CAT1", 1, "VA-1"));
        Thread.currentThread().interrupt();
        try {
            worker.generate(CLAIM);
        } finally {
            Thread.interrupted();
        }

        verify(claims).markGenerationFailed(eq(CLAIM), eq(DocumentStatus.GENERATION_FAILED_FINAL), isNull(),
                anyString(), eq(NOW));
    }

    @Test
    void permanentRenderingFailureIsFinalAtOnce() {
        when(referenceData.activeCategories("VA")).thenReturn(List.of(new ReportCategory("CAT1", "Fees", 1),
                new ReportCategory("CAT2", "Permits", 2)));
        rows.add(row("CAT2", 2, "VA-1"));
        rows.add(row("CAT1", 1, "VA-2"));

        worker.generate(CLAIM);

        verify(claims).markGenerationFailed(eq(CLAIM), eq(DocumentStatus.GENERATION_FAILED_FINAL), isNull(),
                eq("RenderingException: Rows are not in category display order"), eq(NOW));
    }

    @Test
    void aVanishedDocumentIsRecordedAsFinal() {
        when(documents.findById(7L)).thenReturn(Optional.empty());

        worker.generate(CLAIM);

        verify(claims).markGenerationFailed(eq(CLAIM), eq(DocumentStatus.GENERATION_FAILED_FINAL), isNull(),
                anyString(), eq(NOW));
    }

    @Test
    void failuresWhileRecordingAreLeftToLeaseRecovery() {
        rows.add(row("CATX", null, "VA-1"));
        when(claims.markGenerationFailed(any(), any(), any(), anyString(), any()))
                .thenThrow(new IllegalStateException("database down"));

        worker.generate(CLAIM);

        verify(claims).markGenerationFailed(any(), any(), any(), anyString(), any());
    }

    @Test
    void hexEncodesBytes() {
        assertThat(Sha256.toHex(new byte[] {0, 15, (byte) 255})).isEqualTo("000fff");
        assertThat(Sha256.hex("test".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
    }

    private void attempts(int value) {
        ReflectionTestUtils.setField(document, "generationAttempts", (short) value);
    }

    private List<Path> tempFiles() throws Exception {
        if (!Files.exists(tempDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(tempDir)) {
            List<Path> list = new ArrayList<>();
            files.filter(f -> f.getFileName().toString().startsWith("doc-")).forEach(list::add);
            return list;
        }
    }

    private static ReportRow row(String category, Integer order, String caseNumber) {
        return ReportRow.builder().categoryCode(category).displayOrder(order).caseNumber(caseNumber)
                .lastName("Testerson").build();
    }
}
