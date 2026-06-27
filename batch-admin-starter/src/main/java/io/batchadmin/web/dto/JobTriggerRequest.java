package io.batchadmin.web.dto;

/**
 * Request to create a job trigger.
 *
 * @param sourceJob   the job whose completion is observed
 * @param targetJob   the job launched when the condition matches
 * @param condition   {@code SUCCESS} (default), {@code FAILURE} or {@code ANY}
 * @param enabled     whether the trigger is active (defaults to {@code true} when null)
 * @param description optional human-readable note
 */
public record JobTriggerRequest(String sourceJob, String targetJob, String condition,
                                Boolean enabled, String description) {
}
