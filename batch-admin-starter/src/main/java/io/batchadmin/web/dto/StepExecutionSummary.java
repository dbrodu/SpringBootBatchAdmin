package io.batchadmin.web.dto;

import java.time.Instant;

/**
 * View of a single {@code StepExecution}, carrying the read/write counters used for observability.
 */
public record StepExecutionSummary(
        long stepExecutionId,
        String stepName,
        String status,
        String exitCode,
        String exitDescription,
        Instant startTime,
        Instant endTime,
        Long durationMs,
        long readCount,
        long writeCount,
        long commitCount,
        long rollbackCount,
        long filterCount,
        long skipCount) {
}
