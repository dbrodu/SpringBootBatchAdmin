package io.batchadmin.web.dto;

/**
 * Request body to clone an existing job into a new dynamic job.
 *
 * @param newName name for the clone; when blank, {@code <source>-copy} is used
 */
public record CloneJobRequest(String newName) {
}
