package io.batchadmin.domain;

import java.time.Instant;

/**
 * Persisted definition of a job created on the fly. {@code stepsJson} holds the JSON serialization
 * of the ordered list of {@link io.batchadmin.dynamic.StepDefinition step definitions}.
 */
public record JobDefinitionRecord(
        Long id,
        String jobName,
        String description,
        String stepsJson,
        Instant createdAt) {
}
