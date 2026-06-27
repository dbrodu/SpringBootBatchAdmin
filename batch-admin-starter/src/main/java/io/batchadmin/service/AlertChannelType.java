package io.batchadmin.service;

/** Where an alert is delivered. Host applications can register channels for other transports. */
public enum AlertChannelType {

    /** Write the alert to the application log at WARN. Always available. */
    LOG,
    /** POST the alert as JSON to a configured URL (works for Slack/Teams/any webhook receiver). */
    WEBHOOK;

    /** Parses leniently, defaulting to {@link #LOG} for blank/unknown input. */
    public static AlertChannelType parse(String value) {
        if (value == null || value.isBlank()) {
            return LOG;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return LOG;
        }
    }
}
