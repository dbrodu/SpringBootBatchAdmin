package io.batchadmin.domain;

import java.time.Instant;

/**
 * A persisted alert rule: when a job matching {@code jobName} ({@code *} = all) finishes, an alert is
 * raised if the rule's condition holds ({@code FAILURE} status, or {@code DURATION} overrun beyond
 * {@code thresholdMillis}) and delivered through {@code channel} to {@code target}.
 */
public record AlertRuleRecord(
        Long id,
        String jobName,
        String ruleType,
        Long thresholdMillis,
        String channel,
        String target,
        boolean enabled,
        String description,
        Instant createdAt) {
}
