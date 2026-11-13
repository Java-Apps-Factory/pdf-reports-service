package com.example.reporting.orchestration;

import com.example.reporting.data.ReferenceDataRepository;
import com.example.reporting.tracking.DocumentStatus;
import com.example.reporting.tracking.ReportContentStore;
import com.example.reporting.tracking.ReportDocument;
import com.example.reporting.tracking.ReportDocumentRepository;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.ReportRunRepository;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Read side for the API: runs, documents, counts, locality names and stored content. */
@Service
public class ReportQueryService {

    private final ReportRunRepository runs;
    private final ReportDocumentRepository documents;
    private final ReportContentStore content;
    private final ReferenceDataRepository referenceData;

    /**
     * Creates the service.
     *
     * @param runs run repository
     * @param documents document repository
     * @param content PDF store
     * @param referenceData reference data reader
     */
    public ReportQueryService(ReportRunRepository runs, ReportDocumentRepository documents,
            ReportContentStore content, ReferenceDataRepository referenceData) {
        this.runs = runs;
        this.documents = documents;
        this.content = content;
        this.referenceData = referenceData;
    }

    /**
     * Finds a run.
     *
     * @param runId run ID
     * @return the run, or empty
     */
    public Optional<ReportRun> run(long runId) {
        return runs.findById(runId);
    }

    /**
     * Lists runs newest first, optionally for one state.
     *
     * @param stateCode state filter, or empty for all
     * @param pageable page request
     * @return a page of runs
     */
    public Page<ReportRun> runs(Optional<String> stateCode, Pageable pageable) {
        return stateCode.map(state -> runs.findByStateCodeOrderByRunIdDesc(state, pageable))
                .orElseGet(() -> runs.findAllByOrderByRunIdDesc(pageable));
    }

    /**
     * Counts a run's documents per status.
     *
     * @param runId run ID
     * @return statuses present with their counts, in status order
     */
    public Map<DocumentStatus, Long> documentCounts(long runId) {
        Map<DocumentStatus, Long> counts = new EnumMap<>(DocumentStatus.class);
        documents.countByStatus(runId).forEach(c -> counts.put(c.getStatus(), c.getCount()));
        return counts;
    }

    /**
     * Lists a run's documents by FIPS code, optionally in one status.
     *
     * @param runId run ID
     * @param status status filter, or empty for all
     * @param pageable page request
     * @return a page of documents
     */
    public Page<ReportDocument> documents(long runId, Optional<DocumentStatus> status, Pageable pageable) {
        return status.map(s -> documents.findByRunIdAndStatusOrderByFipsCodeAsc(runId, s, pageable))
                .orElseGet(() -> documents.findByRunIdOrderByFipsCodeAsc(runId, pageable));
    }

    /**
     * Finds a document.
     *
     * @param docId document ID
     * @return the document, or empty
     */
    public Optional<ReportDocument> document(long docId) {
        return documents.findById(docId);
    }

    /**
     * Returns the locality name of a FIPS code.
     *
     * @param stateCode state code
     * @param fipsCode FIPS code
     * @return name, or empty if unknown or inactive
     */
    public Optional<String> localityName(String stateCode, String fipsCode) {
        return referenceData.localityName(stateCode, fipsCode);
    }

    /**
     * Tells whether the PDF of a document can be downloaded.
     *
     * @param document the document
     * @return true if content is stored and not purged
     */
    public boolean contentAvailable(ReportDocument document) {
        return document.getContentPurgedAt() == null && content.exists(document.getDocId());
    }

    /**
     * Streams the stored PDF.
     *
     * @param docId document ID
     * @param out destination; not closed
     * @return true if content existed
     */
    public boolean copyContent(long docId, OutputStream out) {
        return content.copyTo(docId, out);
    }
}
