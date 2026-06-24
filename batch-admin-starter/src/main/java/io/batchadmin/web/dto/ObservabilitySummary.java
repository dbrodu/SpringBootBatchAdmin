package io.batchadmin.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Aggregated, dashboard-friendly snapshot of the batch activity.
 *
 * @param totalJobs           number of launchable jobs
 * @param dynamicJobs         number of jobs created on the fly
 * @param activeSchedules     number of enabled cron schedules
 * @param runningExecutions   number of currently running executions
 * @param statusCounts        execution counts grouped by batch status over the recent window
 * @param recentExecutions    most recent executions, newest first
 */
public record ObservabilitySummary(
        int totalJobs,
        int dynamicJobs,
        int activeSchedules,
        int runningExecutions,
        Map<String, Long> statusCounts,
        List<ExecutionSummary> recentExecutions) {
}
