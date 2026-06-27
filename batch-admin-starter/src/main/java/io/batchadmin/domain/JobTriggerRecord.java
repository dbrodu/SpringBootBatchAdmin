package io.batchadmin.domain;

import java.time.Instant;

/**
 * A persisted job trigger: when {@code sourceJob} finishes matching {@code condition}
 * ({@code SUCCESS} / {@code FAILURE} / {@code ANY}), {@code targetJob} is launched. The building
 * block of event-driven job chaining (pipelines).
 *
 * @param inheritParams  whether the source job's parameters are forwarded to the target launch
 * @param parametersJson optional JSON object of extra/static parameters added to the target launch
 *                       (overriding inherited ones on a key clash)
 */
public record JobTriggerRecord(
        Long id,
        String sourceJob,
        String targetJob,
        String condition,
        boolean enabled,
        boolean inheritParams,
        String parametersJson,
        String description,
        Instant createdAt) {
}
