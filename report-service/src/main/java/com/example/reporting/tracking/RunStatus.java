package com.example.reporting.tracking;

/** Run lifecycle (SPEC section 9). */
public enum RunStatus {
    /** Generation phase; the run holds the state lock. */
    RUNNING,
    /** Every document generated or failed; lock released; sends continue. */
    DISPATCHING,
    /** Every document SENT or SUPERSEDED. */
    COMPLETED,
    /** Every document final, at least one failed. */
    COMPLETED_WITH_ERRORS,
    /** Kept for safety; run creation is one transaction, so it is not expected. */
    FAILED
}
