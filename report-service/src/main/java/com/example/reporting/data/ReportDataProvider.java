package com.example.reporting.data;

import java.util.List;
import java.util.function.Consumer;

/**
 * Source of report data (Strategy). Implementations stream rows; they never return all rows of a report as a
 * list (NFR-02).
 */
public interface ReportDataProvider {

    /**
     * Returns every FIPS code of the state that has records, with counts. FIPS codes without records do not
     * appear (FR-02).
     *
     * @param stateCode two-letter state code
     * @return workload rows ordered by FIPS code
     */
    List<FipsWorkload> workload(String stateCode);

    /**
     * Streams the records of one FIPS code in report order (category display order, last name, first name,
     * case number) to {@code consumer}, one at a time.
     *
     * @param stateCode two-letter state code
     * @param fipsCode five-digit FIPS code
     * @param consumer receives each row; exceptions it throws stop the stream and propagate
     * @return the number of rows streamed
     */
    long streamRows(String stateCode, String fipsCode, Consumer<ReportRow> consumer);
}
