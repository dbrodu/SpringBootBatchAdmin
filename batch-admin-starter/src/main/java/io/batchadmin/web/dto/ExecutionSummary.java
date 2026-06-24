package io.batchadmin.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * View of a single {@code JobExecution}, optionally with its step executions.
 */
public record ExecutionSummary(
        long executionId,
        long jobInstanceId,
        String jobName,
        String status,
        String exitCode,
        String exitDescription,
        Instant createTime,
        Instant startTime,
        Instant endTime,
        Long durationMs,
        boolean running,
        boolean restartable,
        Map<String, String> parameters,
        List<StepExecutionSummary> steps) {
}
