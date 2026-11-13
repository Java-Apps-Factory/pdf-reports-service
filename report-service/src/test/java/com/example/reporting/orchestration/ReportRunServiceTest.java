package com.example.reporting.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.reporting.data.FipsWorkload;
import com.example.reporting.data.ReferenceDataRepository;
import com.example.reporting.data.ReportDataProvider;
import com.example.reporting.observability.ReportingMetrics;
import com.example.reporting.profile.ProfileRegistry;
import com.example.reporting.profile.ProfileValidator;
import com.example.reporting.profile.TestProfiles;
import com.example.reporting.tracking.DbClock;
import com.example.reporting.tracking.DocumentStatus;
import com.example.reporting.tracking.ReportDocument;
import com.example.reporting.tracking.ReportDocumentRepository;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.ReportRunRepository;
import com.example.reporting.tracking.RunStatus;
import com.example.reporting.tracking.TriggerType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

/** Stage A rules of SPEC section 8 with mocked persistence. */
class ReportRunServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T19:05:00Z");

    private final ReferenceDataRepository referenceData = mock(ReferenceDataRepository.class);
    private final ReportDataProvider dataProvider = mock(ReportDataProvider.class);
    private final ReportRunRepository runs = mock(ReportRunRepository.class);
    private final ReportDocumentRepository documents = mock(ReportDocumentRepository.class);
    private final RunStateLock lock = mock(RunStateLock.class);
    private final DbClock clock = mock(DbClock.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private ReportRunService service;

    @BeforeEach
    void setUp() {
        ProfileRegistry registry = new ProfileRegistry(TestProfiles.virginiaProperties(Paths.get("tmp")),
                mock(ProfileValidator.class), new DefaultResourceLoader());
        service = new ReportRunService(referenceData, registry, dataProvider, runs, documents, lock, clock,
                new ReportingMetrics(meters));
        when(referenceData.states()).thenReturn(Map.of("VA", true, "MD", false));
        when(referenceData.activeFipsCodes("VA")).thenReturn(Set.of("51059", "51013", "51001"));
        when(clock.now()).thenReturn(NOW);
        when(runs.saveAndFlush(any())).thenAnswer(call -> {
            ReportRun run = call.getArgument(0);
            ReflectionTestUtils.setField(run, "runId", 42L);
            return run;
        });
        when(lock.acquire("VA", 42L)).thenReturn(true);
        when(dataProvider.workload("VA")).thenReturn(List.of(workload("51013", 10, false),
                workload("51059", 30, false), workload("51999", 2, true)));
    }

    @Test
    void createsOnePendingDocumentPerFipsCodeWithRecordsAndDataErrorsForUnknownCodes() {
        ReportRun run = service.startRun(command(List.of()));

        assertThat(run.getRunId()).isEqualTo(42L);
        assertThat(run.getStatus()).isEqualTo(RunStatus.RUNNING);
        assertThat(run.getDataAsOf()).isEqualTo(NOW);
        assertThat(run.getTotalDocuments()).isEqualTo(3);
        assertThat(run.getTriggerType()).isEqualTo(TriggerType.API);
        assertThat(run.getFipsFilter()).isNull();
        List<ReportDocument> saved = savedDocuments();
        assertThat(saved).extracting(ReportDocument::getFipsCode).containsExactly("51013", "51059", "51999");
        assertThat(saved).extracting(ReportDocument::getStatus).containsExactly(DocumentStatus.PENDING,
                DocumentStatus.PENDING, DocumentStatus.DATA_ERROR);
        assertThat(saved.get(2).getLastError()).isEqualTo(ReportRunService.UNKNOWN_FIPS_MESSAGE);
        verify(lock, never()).release(anyString(), anyLong());
        assertThat(meters.counter("reporting.runs.started", "state", "VA", "trigger", "API").count()).isEqualTo(1);
    }

    @Test
    void appliesTheFipsFilterAndSkipsCodesWithoutRecords() {
        ReportRun run = service.startRun(command(List.of("51059", "51001")));

        assertThat(run.getFipsFilter()).isEqualTo("51059,51001");
        assertThat(savedDocuments()).extracting(ReportDocument::getFipsCode).containsExactly("51059");
        assertThat(run.getTotalDocuments()).isEqualTo(1);
    }

    @Test
    void aRunWithoutDocumentsCompletesAtOnceAndReleasesTheLock() {
        ReportRun run = service.startRun(command(List.of("51001")));

        assertThat(run.getTotalDocuments()).isZero();
        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.getCompletedAt()).isEqualTo(NOW);
        verify(lock).release("VA", 42L);
    }

    @Test
    void aHeldStateLockMeansRunAlreadyActive() {
        when(lock.acquire("VA", 42L)).thenReturn(false);
        when(lock.activeRun("VA")).thenReturn(Optional.of(41L));

        assertThatThrownBy(() -> service.startRun(command(List.of())))
                .isInstanceOf(RunAlreadyActiveException.class)
                .hasMessage("State VA already has run 41 in progress.")
                .satisfies(e -> assertThat(((RunAlreadyActiveException) e).getActiveRunId()).contains(41L));
        verify(documents, never()).saveAll(any());
    }

    @Test
    void rejectsInactiveOrUnknownStates() {
        assertThatThrownBy(() -> service.startRun(command("MD", List.of())))
                .isInstanceOf(UnknownStateException.class);
        assertThatThrownBy(() -> service.startRun(command("ZZ", List.of())))
                .isInstanceOf(UnknownStateException.class);
    }

    @Test
    void rejectsMalformedAndForeignFipsCodes() {
        assertThatThrownBy(() -> service.startRun(command(List.of("5105", "51059"))))
                .isInstanceOf(InvalidRunRequestException.class).hasMessageContaining("[5105]");
        assertThatThrownBy(() -> service.startRun(command(List.of("24001"))))
                .isInstanceOf(InvalidRunRequestException.class).hasMessageContaining("[24001]");
    }

    @Test
    void blankAndLongRequestersAreNormalized() {
        ReportRun blank = service.startRun(StartRunCommand.builder().stateCode("VA").triggerType(TriggerType.API)
                .requestedBy("  ").build());
        assertThat(blank.getRequestedBy()).isEqualTo("anonymous");
        ReportRun longName = service.startRun(StartRunCommand.builder().stateCode("VA")
                .triggerType(TriggerType.SCHEDULED).requestedBy("x".repeat(150)).build());
        assertThat(longName.getRequestedBy()).hasSize(100);
    }

    private List<ReportDocument> savedDocuments() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReportDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(documents).saveAll(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    private static StartRunCommand command(List<String> fips) {
        return command("VA", fips);
    }

    private static StartRunCommand command(String state, List<String> fips) {
        return StartRunCommand.builder().stateCode(state).fipsCodes(fips).triggerType(TriggerType.API)
                .requestedBy("jdoe").build();
    }

    private static FipsWorkload workload(String fips, long records, boolean unknown) {
        return FipsWorkload.builder().fipsCode(fips).recordCount(records).unknownFips(unknown).build();
    }
}
