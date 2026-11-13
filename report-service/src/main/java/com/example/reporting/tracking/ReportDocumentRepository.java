package com.example.reporting.tracking;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data access to {@link ReportDocument} (reads and creation only). */
public interface ReportDocumentRepository extends JpaRepository<ReportDocument, Long> {

    /** Status and document count of one run. */
    interface StatusCount {
        /**
         * Returns the status.
         *
         * @return status
         */
        DocumentStatus getStatus();

        /**
         * Returns the number of documents in the status.
         *
         * @return count
         */
        long getCount();
    }

    /**
     * Lists the documents of a run by FIPS code.
     *
     * @param runId run ID
     * @param pageable page request
     * @return a page of documents
     */
    Page<ReportDocument> findByRunIdOrderByFipsCodeAsc(Long runId, Pageable pageable);

    /**
     * Lists the documents of a run in one status by FIPS code.
     *
     * @param runId run ID
     * @param status status
     * @param pageable page request
     * @return a page of documents
     */
    Page<ReportDocument> findByRunIdAndStatusOrderByFipsCodeAsc(Long runId, DocumentStatus status, Pageable pageable);

    /**
     * Counts the documents of a run in any of the statuses.
     *
     * @param runId run ID
     * @param statuses statuses
     * @return count
     */
    long countByRunIdAndStatusIn(Long runId, Collection<DocumentStatus> statuses);

    /**
     * Counts the documents of a run per status.
     *
     * @param runId run ID
     * @return one entry per status present
     */
    @Query("select d.status as status, count(d) as count from ReportDocument d where d.runId = :runId "
            + "group by d.status")
    List<StatusCount> countByStatus(Long runId);

    /**
     * Counts all documents per status, for the cluster-wide gauges.
     *
     * @return one entry per status present
     */
    @Query("select d.status as status, count(d) as count from ReportDocument d group by d.status")
    List<StatusCount> countAllByStatus();
}
