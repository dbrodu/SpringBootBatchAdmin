package io.batchadmin.web.dto;

import java.time.Instant;
import java.util.Map;

/**
 * A job trigger as shown in the API/GUI: when {@code sourceJob} finishes matching {@code condition},
 * {@code targetJob} is launched.
 *
 * @param condition     {@code SUCCESS}, {@code FAILURE} or {@code ANY}
 * @param inheritParams whether the source job's parameters are forwarded to the target launch
 * @param parameters    extra/static parameters added to the target launch
 */
public record JobTriggerInfo(long id, String sourceJob, String targetJob, String condition,
                             boolean enabled, boolean inheritParams, Map<String, String> parameters,
                             String description, Instant createdAt) {
}
