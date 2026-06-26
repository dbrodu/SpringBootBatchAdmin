package io.batchadmin.web.dto;

import io.batchadmin.dynamic.StepDefinition;
import java.time.Instant;
import java.util.List;

/**
 * A historical version of a dynamic job's definition, for the history/rollback views.
 *
 * @param version     the version number (1-based; higher is newer)
 * @param description the description captured at that version
 * @param steps       the step definitions captured at that version
 * @param author      who recorded the version (audit metadata)
 * @param changeType  the kind of change that produced it: CREATE / EDIT / ROLLBACK / IMPORT /
 *                    BASELINE (audit metadata)
 * @param changeNote  an optional free-text note describing the change (audit metadata)
 * @param createdAt   when the version was recorded
 * @param current     whether this is the version currently registered
 */
public record JobVersionInfo(int version, String description, List<StepDefinition> steps,
                             String author, String changeType, String changeNote,
                             Instant createdAt, boolean current) {
}
