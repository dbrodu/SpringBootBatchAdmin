package io.batchadmin.web.dto;

/**
 * One actual step a composed job would run, as computed by a dry-run preview.
 *
 * @param stepName the name the step will have when the job runs
 * @param type     the building-block type the step came from (e.g. {@code log}, {@code sql-export},
 *                 {@code job:invoiceJob})
 * @param source   a short human-readable description of where the step comes from
 */
public record JobStepPreview(String stepName, String type, String source) {
}
