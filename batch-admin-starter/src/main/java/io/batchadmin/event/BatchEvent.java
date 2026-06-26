package io.batchadmin.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;

/**
 * Immutable description of a job-lifecycle event emitted by the component. Published through a
 * {@link BatchEventPublisher} so it can be consumed in-process (Spring {@code @EventListener}) or
 * fanned out to a message broker (e.g. RabbitMQ).
 *
 * <p>{@link Serializable} so it survives the default broker message converters out of the box; it is
 * also a plain record, hence trivially JSON-serializable when a JSON converter is configured.</p>
 */
public record BatchEvent(
        BatchEventType type,
        String jobName,
        Long executionId,
        Long jobInstanceId,
        String status,
        String exitCode,
        Instant timestamp,
        Map<String, String> parameters) implements Serializable {

    /** Builds an event of the given type from a Spring Batch {@link JobExecution} snapshot. */
    public static BatchEvent from(BatchEventType type, JobExecution execution) {
        Map<String, String> params = new LinkedHashMap<>();
        execution.getJobParameters().getParameters().forEach((key, value) ->
                params.put(key, String.valueOf(((JobParameter<?>) value).getValue())));
        String jobName = execution.getJobInstance() != null
                ? execution.getJobInstance().getJobName() : null;
        Long instanceId = execution.getJobInstance() != null
                ? execution.getJobInstance().getInstanceId() : null;
        return new BatchEvent(
                type,
                jobName,
                execution.getId(),
                instanceId,
                execution.getStatus() != null ? execution.getStatus().toString() : null,
                execution.getExitStatus() != null ? execution.getExitStatus().getExitCode() : null,
                Instant.now(),
                params);
    }
}
