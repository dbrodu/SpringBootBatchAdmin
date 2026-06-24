package io.batchadmin.web.dto;

import java.util.Map;

/**
 * Request body to create or update a cron schedule for a job.
 */
public record ScheduleRequest(
        String jobName,
        String cron,
        String description,
        Boolean enabled,
        Map<String, String> parameters) {

    public ScheduleRequest {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
