package com.example.reporting.transmission;

/** Delivers one report message (Strategy); the broker accepting it counts as sent. */
public interface ReportTransmitter {

    /**
     * Sends the message and commits.
     *
     * @param message the report message
     * @return the message ID assigned by the broker
     */
    String send(ReportMessage message);
}
