package io.batchadmin.service;

/** What an alert rule reacts to. */
public enum AlertRuleType {

    /** The job finished in a failure status (FAILED / ABANDONED). */
    FAILURE,
    /** The job's run time exceeded an SLA threshold (overran). */
    DURATION;

    /** Parses leniently, defaulting to {@link #FAILURE} for blank/unknown input. */
    public static AlertRuleType parse(String value) {
        if (value == null || value.isBlank()) {
            return FAILURE;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FAILURE;
        }
    }
}
