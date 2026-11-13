package com.example.reporting.orchestration;

/** A manual retransmit cannot be done; {@link #getReason()} decides the HTTP status. */
public final class RetransmitRefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Why the retransmit was refused. */
    public enum Reason {
        /** The document or run does not exist (404). */
        NOT_FOUND,
        /** The document is superseded or not in SEND_FAILED / SEND_FAILED_FINAL (409). */
        CONFLICT,
        /** The stored PDF was purged (410). */
        GONE
    }

    private final Reason reason;

    /**
     * Creates the exception.
     *
     * @param reason why it was refused
     * @param message message naming ids and statuses only
     */
    public RetransmitRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
