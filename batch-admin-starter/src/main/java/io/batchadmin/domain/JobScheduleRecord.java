package io.batchadmin.domain;

import java.time.Instant;

/**
 * Persisted cron schedule for a job. {@code parametersJson} holds an optional JSON object of job
 * parameters used at each trigger.
 */
public record JobScheduleRecord(
        Long id,
        String jobName,
        String cron,
        String parametersJson,
        boolean enabled,
        String description,
        Instant createdAt) {
}
