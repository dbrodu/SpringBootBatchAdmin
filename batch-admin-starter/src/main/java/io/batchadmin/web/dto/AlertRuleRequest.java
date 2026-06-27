package io.batchadmin.web.dto;

/**
 * Request to create an alert rule.
 *
 * @param jobName         the job to watch, or {@code *} / blank for every job
 * @param ruleType        {@code FAILURE} (default) or {@code DURATION}
 * @param thresholdMillis SLA threshold in ms (required for {@code DURATION})
 * @param channel         {@code LOG} (default) or {@code WEBHOOK}
 * @param target          channel target (e.g. webhook URL); required for {@code WEBHOOK}
 * @param enabled         whether the rule is active (defaults to {@code true} when null)
 * @param description     optional human-readable note
 */
public record AlertRuleRequest(String jobName, String ruleType, Long thresholdMillis, String channel,
                               String target, Boolean enabled, String description) {
}
