package com.example.reporting.tracking;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@link ReportRun}. */
public interface ReportRunRepository extends JpaRepository<ReportRun, Long> {

    /**
     * Lists the runs of a state, newest first.
     *
     * @param stateCode state code
     * @param pageable page request
     * @return a page of runs
     */
    Page<ReportRun> findByStateCodeOrderByRunIdDesc(String stateCode, Pageable pageable);

    /**
     * Lists all runs, newest first.
     *
     * @param pageable page request
     * @return a page of runs
     */
    Page<ReportRun> findAllByOrderByRunIdDesc(Pageable pageable);

    /**
     * Finds runs in a status, oldest first.
     *
     * @param status run status
     * @return runs
     */
    List<ReportRun> findByStatusOrderByRunIdAsc(RunStatus status);
}
