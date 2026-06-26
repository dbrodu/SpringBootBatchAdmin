package io.batchadmin.web.dto;

import java.util.List;

/**
 * Result of a dry-run preview of a composed job: the ordered, fully expanded list of steps it would
 * run (with {@code job:<name>} whole-job blocks expanded into their constituent steps), without
 * creating or registering anything.
 *
 * @param jobName   the proposed job name (may be blank when previewing before naming)
 * @param stepCount the number of actual steps the job would run
 * @param steps     the expanded steps, in execution order
 */
public record JobPreview(String jobName, int stepCount, List<JobStepPreview> steps) {
}
