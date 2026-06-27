package io.batchadmin.domain;

import java.time.Instant;

/**
 * One historical snapshot of a dynamic job's definition. A new version is appended every time the job
 * is created, edited or rolled back, so earlier definitions can be inspected and restored.
 *
 * @param author     who recorded the version (authenticated user, or {@code system} for background
 *                   actions); audit metadata
 * @param changeType the kind of change that produced this version (see {@code VersionChangeType});
 *                   audit metadata
 * @param changeNote an optional free-text note describing the change; audit metadata
 */
public record JobDefinitionVersionRecord(
        Long id,
        String jobName,
        int version,
        String description,
        String stepsJson,
        String author,
        String changeType,
        String changeNote,
        Instant createdAt) {
}
