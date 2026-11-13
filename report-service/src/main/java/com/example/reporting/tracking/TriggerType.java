package com.example.reporting.tracking;

/** What started a run. */
public enum TriggerType {
    /** The daily per-state schedule. */
    SCHEDULED,
    /** A REST API request. */
    API
}
