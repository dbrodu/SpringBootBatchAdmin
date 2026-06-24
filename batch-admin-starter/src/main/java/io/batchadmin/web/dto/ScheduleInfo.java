package io.batchadmin.web.dto;

import java.time.Instant;
import java.util.Map;

/**
 * View of a persisted cron schedule, including the next computed fire time when available.
 */
public record ScheduleInfo(
        long id,
        String jobName,
        String cron,
        String description,
        boolean enabled,
        Map<String, String> parameters,
        Instant nextExecution,
        Instant createdAt) {
}
