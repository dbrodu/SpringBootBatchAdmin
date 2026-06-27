package io.batchadmin.service;

import org.springframework.batch.core.BatchStatus;

/** When a trigger fires, relative to the source job's terminal status. */
public enum TriggerCondition {

    /** Fire only when the source job completed successfully. */
    SUCCESS,
    /** Fire only when the source job failed (FAILED / ABANDONED). */
    FAILURE,
    /** Fire whenever the source job finishes, whatever its status. */
    ANY;

    /** Whether this condition matches the given terminal batch status. */
    public boolean matches(BatchStatus status) {
        return switch (this) {
            case ANY -> true;
            case SUCCESS -> status == BatchStatus.COMPLETED;
            case FAILURE -> status == BatchStatus.FAILED || status == BatchStatus.ABANDONED;
        };
    }

    /** Parses leniently, defaulting to {@link #SUCCESS} for blank/unknown input. */
    public static TriggerCondition parse(String value) {
        if (value == null || value.isBlank()) {
            return SUCCESS;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SUCCESS;
        }
    }
}
