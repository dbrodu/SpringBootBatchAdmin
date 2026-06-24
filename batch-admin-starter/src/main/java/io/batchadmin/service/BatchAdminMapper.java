package io.batchadmin.service;

import io.batchadmin.web.dto.ExecutionSummary;
import io.batchadmin.web.dto.StepExecutionSummary;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.StepExecution;

/**
 * Converts Spring Batch domain objects into the immutable DTOs exposed by the REST API.
 */
public final class BatchAdminMapper {

    private BatchAdminMapper() {
    }

    public static ExecutionSummary toExecutionSummary(JobExecution execution, boolean includeSteps) {
        Instant start = toInstant(execution.getStartTime());
        Instant end = toInstant(execution.getEndTime());
        Long duration = (start != null && end != null) ? Duration.between(start, end).toMillis() : null;

        List<StepExecutionSummary> steps = includeSteps
                ? execution.getStepExecutions().stream().map(BatchAdminMapper::toStepSummary).toList()
                : List.of();

        BatchStatus status = execution.getStatus();
        boolean restartable = status == BatchStatus.FAILED || status == BatchStatus.STOPPED;

        return new ExecutionSummary(
                execution.getId(),
                execution.getJobInstance() != null ? execution.getJobInstance().getInstanceId() : -1L,
                execution.getJobInstance() != null ? execution.getJobInstance().getJobName() : null,
                status.name(),
                execution.getExitStatus() != null ? execution.getExitStatus().getExitCode() : null,
                execution.getExitStatus() != null ? execution.getExitStatus().getExitDescription() : null,
                toInstant(execution.getCreateTime()),
                start,
                end,
                duration,
                execution.isRunning(),
                restartable,
                toParameterMap(execution),
                steps);
    }

    public static StepExecutionSummary toStepSummary(StepExecution step) {
        Instant start = toInstant(step.getStartTime());
        Instant end = toInstant(step.getEndTime());
        Long duration = (start != null && end != null) ? Duration.between(start, end).toMillis() : null;
        return new StepExecutionSummary(
                step.getId(),
                step.getStepName(),
                step.getStatus().name(),
                step.getExitStatus() != null ? step.getExitStatus().getExitCode() : null,
                step.getExitStatus() != null ? step.getExitStatus().getExitDescription() : null,
                start,
                end,
                duration,
                step.getReadCount(),
                step.getWriteCount(),
                step.getCommitCount(),
                step.getRollbackCount(),
                step.getFilterCount(),
                step.getSkipCount());
    }

    public static Map<String, String> toParameterMap(JobExecution execution) {
        Map<String, String> result = new LinkedHashMap<>();
        if (execution.getJobParameters() != null) {
            for (Map.Entry<String, JobParameter<?>> entry : execution.getJobParameters().getParameters().entrySet()) {
                Object value = entry.getValue() != null ? entry.getValue().getValue() : null;
                result.put(entry.getKey(), value != null ? String.valueOf(value) : null);
            }
        }
        return result;
    }

    public static Instant toInstant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
