package io.batchadmin.logs;

import java.time.Instant;

/**
 * A single captured log event associated with a job execution.
 *
 * @param timestamp when the event was logged
 * @param level     log level name (TRACE, DEBUG, INFO, WARN, ERROR)
 * @param logger    logger name (usually a class name)
 * @param thread    name of the thread that emitted the event
 * @param message   formatted message, including a throwable summary when present
 */
public record JobLogEntry(
        Instant timestamp,
        String level,
        String logger,
        String thread,
        String message) {
}
