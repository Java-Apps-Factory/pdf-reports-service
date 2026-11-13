package com.example.reporting.api;

import com.example.reporting.api.dto.DocumentResponse;
import com.example.reporting.api.dto.PageResponse;
import com.example.reporting.api.dto.RunResponse;
import com.example.reporting.api.dto.StartRunRequest;
import com.example.reporting.orchestration.ReportQueryService;
import com.example.reporting.orchestration.ReportRunService;
import com.example.reporting.orchestration.StartRunCommand;
import com.example.reporting.tracking.DocumentStatus;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.TriggerType;
import java.net.URI;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Report runs: start, list, detail and documents (SPEC section 14). */
@RestController
@RequestMapping("/api/v1/report-runs")
@Validated
public class ReportRunController {

    /** Header carrying the caller's identity until security is added. */
    public static final String REQUESTED_BY = "X-Requested-By";

    private static final int MAX_PAGE_SIZE = 200;

    private final ReportRunService runService;
    private final ReportQueryService queries;
    private final ApiMapper mapper;

    /**
     * Creates the controller.
     *
     * @param runService starts runs
     * @param queries read side
     * @param mapper response mapper
     */
    public ReportRunController(ReportRunService runService, ReportQueryService queries, ApiMapper mapper) {
        this.runService = runService;
        this.queries = queries;
        this.mapper = mapper;
    }

    /**
     * Starts a run (202, Location of the run).
     *
     * @param request state and optional FIPS filter
     * @param requestedBy caller identity, default {@code anonymous}
     * @return the new run
     */
    @PostMapping
    public ResponseEntity<RunResponse> start(@Valid @RequestBody StartRunRequest request,
            @RequestHeader(value = REQUESTED_BY, required = false, defaultValue = "anonymous") String requestedBy) {
        ReportRun run = runService.startRun(StartRunCommand.builder()
                .stateCode(request.getStateCode())
                .fipsCodes(request.getFipsCodes())
                .triggerType(TriggerType.API)
                .requestedBy(requestedBy)
                .build());
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/report-runs/" + run.getRunId()))
                .body(mapper.run(run, null));
    }

    /**
     * Lists runs newest first.
     *
     * @param stateCode optional state filter
     * @param page page number from 0
     * @param size page size, at most 200
     * @return a page of runs
     */
    @GetMapping
    public PageResponse<RunResponse> list(
            @RequestParam(required = false) @Pattern(regexp = "[A-Z]{2}") String stateCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return PageResponse.of(queries.runs(Optional.ofNullable(stateCode), PageRequest.of(page, size)),
                run -> mapper.run(run, null));
    }

    /**
     * Returns a run with its document counts by status.
     *
     * @param runId run ID
     * @return the run
     */
    @GetMapping("/{runId}")
    public RunResponse detail(@PathVariable long runId) {
        ReportRun run = queries.run(runId).orElseThrow(() -> new ApiExceptions.NotFound("Run " + runId
                + " does not exist."));
        return mapper.run(run, queries.documentCounts(runId));
    }

    /**
     * Lists the documents of a run by FIPS code.
     *
     * @param runId run ID
     * @param status optional status filter
     * @param page page number from 0
     * @param size page size, at most 200
     * @return a page of documents
     */
    @GetMapping("/{runId}/documents")
    public PageResponse<DocumentResponse> documents(@PathVariable long runId,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        if (queries.run(runId).isEmpty()) {
            throw new ApiExceptions.NotFound("Run " + runId + " does not exist.");
        }
        return PageResponse.of(queries.documents(runId, Optional.ofNullable(status), PageRequest.of(page, size)),
                document -> mapper.document(document,
                        queries.localityName(document.getStateCode(), document.getFipsCode()).orElse(null),
                        document.getContentPurgedAt() == null && document.getGeneratedAt() != null));
    }
}
