package io.batchadmin.web.dto;

import java.time.Instant;

/**
 * A fired alert: the outcome of an alert rule matching a finished job execution. Delivered to a
 * channel and kept in a small recent-alerts buffer for the GUI/API.
 *
 * @param ruleType {@code FAILURE} or {@code DURATION}
 * @param channel  the channel it was delivered to ({@code LOG} / {@code WEBHOOK})
 */
public record AlertNotification(String jobName, Long executionId, String ruleType, String status,
                                long durationMs, String channel, String message, Instant timestamp) {
}
