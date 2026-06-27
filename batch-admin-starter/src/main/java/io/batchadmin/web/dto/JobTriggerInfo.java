package io.batchadmin.web.dto;

import java.time.Instant;

/**
 * A job trigger as shown in the API/GUI: when {@code sourceJob} finishes matching {@code condition},
 * {@code targetJob} is launched.
 *
 * @param condition {@code SUCCESS}, {@code FAILURE} or {@code ANY}
 */
public record JobTriggerInfo(long id, String sourceJob, String targetJob, String condition,
                             boolean enabled, String description, Instant createdAt) {
}
