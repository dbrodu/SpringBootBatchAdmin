package io.batchadmin.web.dto;

import java.util.List;

/**
 * A comparison between two versions of a dynamic job's definition, for the diff view.
 *
 * @param jobName             the job being compared
 * @param from                the older (left) version's metadata
 * @param to                  the newer (right) version's metadata
 * @param descriptionChanged  whether the description differs between the two versions
 * @param steps               the step-level diff, in display order
 */
public record JobVersionDiff(String jobName, JobVersionInfo from, JobVersionInfo to,
                             boolean descriptionChanged, List<Line> steps) {

    /**
     * One line of the step-level diff.
     *
     * @param op   {@code unchanged}, {@code added} (present only in {@code to}) or {@code removed}
     *             (present only in {@code from})
     * @param text the step rendered as {@code name = type (k=v, …)}
     */
    public record Line(String op, String text) {
    }
}
