package io.batchadmin.web.dto;

import java.time.Instant;

/**
 * An alert rule as shown in the API/GUI.
 *
 * @param jobName         the job it applies to, or {@code *} for every job
 * @param ruleType        {@code FAILURE} or {@code DURATION}
 * @param thresholdMillis the SLA threshold in ms (only meaningful for {@code DURATION})
 * @param channel         {@code LOG} or {@code WEBHOOK}
 * @param target          the channel target (e.g. the webhook URL); may be empty for {@code LOG}
 */
public record AlertRuleInfo(long id, String jobName, String ruleType, Long thresholdMillis,
                            String channel, String target, boolean enabled, String description,
                            Instant createdAt) {
}
