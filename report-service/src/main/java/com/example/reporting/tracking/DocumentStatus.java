package com.example.reporting.tracking;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Document lifecycle and its allowed transitions (SPEC section 9). Every status change is checked with
 * {@link #canTransitionTo} before the conditional UPDATE runs.
 */
public enum DocumentStatus {
    /** Waiting for generation. */
    PENDING,
    /** Claimed by a generation worker; leased. */
    GENERATING,
    /** Generation failed transiently; retried after NEXT_ATTEMPT_AT. */
    GENERATION_FAILED,
    /** Generation failed permanently or ran out of attempts. */
    GENERATION_FAILED_FINAL,
    /** The data cannot be reported (unknown FIPS code or category). */
    DATA_ERROR,
    /** PDF stored; waiting to be sent. */
    GENERATED,
    /** Claimed by a send worker; leased. */
    SENDING,
    /** Send failed transiently; retried after NEXT_ATTEMPT_AT. */
    SEND_FAILED,
    /** Send failed permanently or ran out of attempts. */
    SEND_FAILED_FINAL,
    /** The broker accepted the message. */
    SENT,
    /** A newer document for the same state and FIPS code exists (FR-15). */
    SUPERSEDED;

    private static final Map<DocumentStatus, Set<DocumentStatus>> TRANSITIONS = transitions();
    private static final Set<DocumentStatus> FINAL = Collections.unmodifiableSet(EnumSet.of(
            GENERATION_FAILED_FINAL, DATA_ERROR, SEND_FAILED_FINAL, SENT, SUPERSEDED));

    /**
     * Tells whether this status may change to {@code target}.
     *
     * @param target the new status
     * @return true if section 9 allows the transition
     */
    public boolean canTransitionTo(DocumentStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /**
     * Tells whether a new document may start in this status.
     *
     * @return true for PENDING and DATA_ERROR
     */
    public boolean isInitial() {
        return this == PENDING || this == DATA_ERROR;
    }

    /**
     * Tells whether the status is final. The only exit from a final status is a manual retransmit from
     * SEND_FAILED_FINAL.
     *
     * @return true for final statuses
     */
    public boolean isFinal() {
        return FINAL.contains(this);
    }

    /**
     * Returns the final statuses.
     *
     * @return unmodifiable set
     */
    public static Set<DocumentStatus> finalStatuses() {
        return Collections.unmodifiableSet(FINAL);
    }

    /**
     * Throws unless the transition is allowed.
     *
     * @param target the new status
     * @throws IllegalStateException for a forbidden transition
     */
    public void requireTransitionTo(DocumentStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Forbidden document transition " + this + " -> " + target);
        }
    }

    private static Map<DocumentStatus, Set<DocumentStatus>> transitions() {
        Map<DocumentStatus, Set<DocumentStatus>> map = new EnumMap<>(DocumentStatus.class);
        for (DocumentStatus status : values()) {
            map.put(status, EnumSet.noneOf(DocumentStatus.class));
        }
        map.get(PENDING).add(GENERATING);
        map.get(GENERATION_FAILED).add(GENERATING);
        map.get(GENERATING).addAll(EnumSet.of(GENERATED, DATA_ERROR, GENERATION_FAILED, GENERATION_FAILED_FINAL));
        map.get(GENERATED).add(SENDING);
        map.get(SEND_FAILED).addAll(EnumSet.of(SENDING, SEND_FAILED));
        map.get(SENDING).addAll(EnumSet.of(SUPERSEDED, SENT, SEND_FAILED, SEND_FAILED_FINAL));
        map.get(SEND_FAILED_FINAL).add(SEND_FAILED);
        map.replaceAll((status, targets) -> Collections.unmodifiableSet(targets));
        return Collections.unmodifiableMap(map);
    }
}
