package io.batchadmin.domain;

import java.time.Instant;

/**
 * One historical snapshot of a dynamic job's definition. A new version is appended every time the job
 * is created, edited or rolled back, so earlier definitions can be inspected and restored.
 */
public record JobDefinitionVersionRecord(
        Long id,
        String jobName,
        int version,
        String description,
        String stepsJson,
        Instant createdAt) {
}
