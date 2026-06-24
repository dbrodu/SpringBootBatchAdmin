package io.batchadmin.web.dto;

import java.time.Instant;

/**
 * Lightweight view of a registered job for list screens.
 *
 * @param name           job name
 * @param dynamic        whether the job was created on the fly through this component
 * @param launchable     whether the job is currently registered and can be launched
 * @param instanceCount  number of job instances created so far
 * @param running        whether at least one execution is currently running
 * @param lastStatus     batch status of the most recent execution, or {@code null}
 * @param lastExecution  start time of the most recent execution, or {@code null}
 */
public record JobSummary(
        String name,
        boolean dynamic,
        boolean launchable,
        long instanceCount,
        boolean running,
        String lastStatus,
        Instant lastExecution) {
}
