package io.batchadmin.domain;

import java.time.Instant;

/**
 * A persisted job trigger: when {@code sourceJob} finishes matching {@code condition}
 * ({@code SUCCESS} / {@code FAILURE} / {@code ANY}), {@code targetJob} is launched. The building
 * block of event-driven job chaining (pipelines).
 */
public record JobTriggerRecord(
        Long id,
        String sourceJob,
        String targetJob,
        String condition,
        boolean enabled,
        String description,
        Instant createdAt) {
}
