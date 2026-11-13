package com.example.reporting.orchestration;

import com.example.reporting.data.FipsWorkload;
import com.example.reporting.data.ReferenceDataRepository;
import com.example.reporting.data.ReportDataProvider;
import com.example.reporting.observability.ReportingMetrics;
import com.example.reporting.profile.ProfileRegistry;
import com.example.reporting.tracking.DbClock;
import com.example.reporting.tracking.DocumentStatus;
import com.example.reporting.tracking.ReportDocument;
import com.example.reporting.tracking.ReportDocumentRepository;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.ReportRunRepository;
import com.example.reporting.tracking.RunStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stage A of SPEC section 8: creates a run and one document per FIPS code with records, in one transaction.
 * Either everything is committed or nothing is.
 */
@Service
public class ReportRunService {

    /** Message stored on documents whose FIPS code is not in RPT_FIPS (section 8). */
    public static final String UNKNOWN_FIPS_MESSAGE = "FIPS code not in RPT_FIPS for state";

    private static final Logger LOG = LoggerFactory.getLogger(ReportRunService.class);
    private static final Pattern FIPS = Pattern.compile("^\\d{5}$");
    private static final int MAX_REQUESTED_BY = 100;

    private final ReferenceDataRepository referenceData;
    private final ProfileRegistry profiles;
    private final ReportDataProvider dataProvider;
    private final ReportRunRepository runs;
    private final ReportDocumentRepository documents;
    private final RunStateLock stateLock;
    private final DbClock dbClock;
    private final ReportingMetrics metrics;

    /**
     * Creates the service.
     *
     * @param referenceData reference data reader
     * @param profiles state profiles
     * @param dataProvider workload source
     * @param runs run repository
     * @param documents document repository
     * @param stateLock per-state run lock
     * @param dbClock database clock
     * @param metrics meters
     */
    public ReportRunService(ReferenceDataRepository referenceData, ProfileRegistry profiles,
            ReportDataProvider dataProvider, ReportRunRepository runs, ReportDocumentRepository documents,
            RunStateLock stateLock, DbClock dbClock, ReportingMetrics metrics) {
        this.referenceData = referenceData;
        this.profiles = profiles;
        this.dataProvider = dataProvider;
        this.runs = runs;
        this.documents = documents;
        this.stateLock = stateLock;
        this.dbClock = dbClock;
        this.metrics = metrics;
    }

    /**
     * Starts a run: validates the request, inserts the run, takes the state lock, and inserts one PENDING
     * document per FIPS code with records (DATA_ERROR for unknown FIPS codes). A run without documents is
     * completed at once and releases the lock.
     *
     * @param command what to run
     * @return the new run
     * @throws UnknownStateException if the state is not active or has no profile
     * @throws InvalidRunRequestException if the FIPS filter is malformed or names codes of another state
     * @throws RunAlreadyActiveException if another run of the state is in its generation phase
     */
    @Transactional
    public ReportRun startRun(StartRunCommand command) {
        String state = command.stateCode();
        if (!Boolean.TRUE.equals(referenceData.states().get(state)) || profiles.profile(state).isEmpty()) {
            throw new UnknownStateException("State " + state + " is not an active state with a profile.");
        }
        Set<String> filter = validateFilter(state, command.fipsCodes());

        Instant now = dbClock.now();
        ReportRun run = runs.saveAndFlush(ReportRun.start(state, command.triggerType(),
                abbreviate(command.requestedBy()), filter.isEmpty() ? null : String.join(",", filter), now));
        if (!stateLock.acquire(state, run.getRunId())) {
            throw new RunAlreadyActiveException(state, stateLock.activeRun(state));
        }

        List<ReportDocument> created = new ArrayList<>();
        int dataErrors = 0;
        for (FipsWorkload workload : dataProvider.workload(state)) {
            if (!filter.isEmpty() && !filter.contains(workload.fipsCode())) {
                continue;
            }
            if (workload.unknownFips()) {
                dataErrors++;
                created.add(ReportDocument.create(run.getRunId(), state, workload.fipsCode(),
                        DocumentStatus.DATA_ERROR, UNKNOWN_FIPS_MESSAGE, now));
            } else {
                created.add(ReportDocument.create(run.getRunId(), state, workload.fipsCode(), DocumentStatus.PENDING,
                        null, now));
            }
        }
        documents.saveAll(created);
        run.setTotalDocuments(created.size());
        if (created.isEmpty()) {
            run.moveTo(RunStatus.COMPLETED, now);
            stateLock.release(state, run.getRunId());
        }
        metrics.runStarted(state, command.triggerType());
        LOG.info("Run {} started for state {} by {} trigger: {} documents, {} data errors", run.getRunId(), state,
                command.triggerType(), created.size(), dataErrors);
        return run;
    }

    private Set<String> validateFilter(String state, List<String> codes) {
        Set<String> filter = new LinkedHashSet<>();
        List<String> malformed = new ArrayList<>();
        for (String code : codes) {
            if (code == null || !FIPS.matcher(code).matches()) {
                malformed.add(String.valueOf(code));
            } else {
                filter.add(code);
            }
        }
        if (!malformed.isEmpty()) {
            throw new InvalidRunRequestException("FIPS codes must have five digits: " + malformed);
        }
        if (!filter.isEmpty()) {
            Set<String> known = referenceData.activeFipsCodes(state);
            List<String> foreign = filter.stream().filter(code -> !known.contains(code)).collect(Collectors.toList());
            if (!foreign.isEmpty()) {
                throw new InvalidRunRequestException("FIPS codes not active for state " + state + ": " + foreign);
            }
        }
        return filter;
    }

    private static String abbreviate(String requestedBy) {
        String value = requestedBy.isBlank() ? "anonymous" : requestedBy.strip();
        return value.length() <= MAX_REQUESTED_BY ? value : value.substring(0, MAX_REQUESTED_BY);
    }
}
