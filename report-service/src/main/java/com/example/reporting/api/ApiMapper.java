package com.example.reporting.api;

import com.example.reporting.api.dto.DocumentResponse;
import com.example.reporting.api.dto.RunResponse;
import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.tracking.DocumentStatus;
import com.example.reporting.tracking.ReportDocument;
import com.example.reporting.tracking.ReportRun;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Maps tracking entities to API responses; timestamps are ISO-8601 with offset in the business zone. */
@Component
public class ApiMapper {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm");

    private final ZoneId zone;

    /**
     * Creates the mapper.
     *
     * @param properties reporting configuration (business zone)
     */
    public ApiMapper(ReportingProperties properties) {
        this.zone = properties.getBusinessZone();
    }

    /**
     * Maps a run.
     *
     * @param run the run
     * @param counts documents per status, or {@code null} for the summary view
     * @return response
     */
    public RunResponse run(ReportRun run, Map<DocumentStatus, Long> counts) {
        Map<String, Long> byStatus = null;
        if (counts != null) {
            byStatus = new LinkedHashMap<>();
            for (Map.Entry<DocumentStatus, Long> entry : counts.entrySet()) {
                byStatus.put(entry.getKey().name(), entry.getValue());
            }
        }
        return new RunResponse(run.getRunId(), run.getStateCode(), run.getStatus().name(),
                run.getTriggerType().name(), run.getRequestedBy(), time(run.getDataAsOf()), time(run.getCreatedAt()),
                time(run.getCompletedAt()), run.getTotalDocuments(), byStatus);
    }

    /**
     * Maps a document.
     *
     * @param document the document
     * @param localityName locality name, may be {@code null}
     * @param contentAvailable whether the PDF can be downloaded
     * @return response
     */
    public DocumentResponse document(ReportDocument document, String localityName, boolean contentAvailable) {
        return DocumentResponse.builder()
                .identity(document.getDocId(), document.getRunId(), document.getStateCode(), document.getFipsCode(),
                        localityName)
                .status(document.getStatus().name(), document.getGenerationAttempts(), document.getSendAttempts(),
                        time(document.getNextAttemptAt()), document.getLastError())
                .content(document.getRecordCount(), document.getPageCount(), document.getSizeBytes(),
                        document.getSha256() == null ? null : document.getSha256().trim(), contentAvailable)
                .times(time(document.getGeneratedAt()), time(document.getSentAt()))
                .build();
    }

    /**
     * Returns the download file name {@code {STATE}_{FIPS}_{yyyyMMdd'T'HHmm}_{docId}.pdf} (SPEC section 11).
     *
     * @param document the document
     * @param dataAsOf the run's data-as-of time
     * @return file name
     */
    public String fileName(ReportDocument document, Instant dataAsOf) {
        return document.getStateCode() + "_" + document.getFipsCode() + "_"
                + FILE_TIME.format(dataAsOf.atZone(zone)) + "_" + document.getDocId() + ".pdf";
    }

    private OffsetDateTime time(Instant instant) {
        return instant == null ? null : instant.atZone(zone).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS);
    }
}
