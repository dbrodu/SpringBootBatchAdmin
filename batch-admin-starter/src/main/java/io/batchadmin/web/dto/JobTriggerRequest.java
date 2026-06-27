package io.batchadmin.web.dto;

import java.util.Map;

/**
 * Request to create a job trigger.
 *
 * @param sourceJob     the job whose completion is observed
 * @param targetJob     the job launched when the condition matches
 * @param condition     {@code SUCCESS} (default), {@code FAILURE} or {@code ANY}
 * @param enabled       whether the trigger is active (defaults to {@code true} when null)
 * @param inheritParams whether to forward the source job's parameters to the target launch
 * @param parameters    optional extra/static parameters added to the target launch (override
 *                      inherited ones on a key clash)
 * @param description   optional human-readable note
 */
public record JobTriggerRequest(String sourceJob, String targetJob, String condition,
                                Boolean enabled, Boolean inheritParams, Map<String, String> parameters,
                                String description) {
}
