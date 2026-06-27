package io.batchadmin.service;

import io.batchadmin.autoconfigure.BatchAdminProperties;
import io.batchadmin.domain.AlertRuleDao;
import io.batchadmin.domain.AlertRuleRecord;
import io.batchadmin.web.dto.AlertNotification;
import io.batchadmin.web.dto.AlertRuleInfo;
import io.batchadmin.web.dto.AlertRuleRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;

/**
 * Failure / SLA alerting: persisted {@link AlertRuleRecord rules} that, when a job finishes, raise an
 * alert if the job failed ({@code FAILURE}) or overran an SLA threshold ({@code DURATION}). Alerts are
 * delivered through pluggable {@link AlertChannel channels} (log, webhook, …) and the most recent ones
 * are kept in memory for the GUI/API.
 */
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRuleDao ruleDao;
    private final Map<AlertChannelType, AlertChannel> channels = new EnumMap<>(AlertChannelType.class);
    private final BatchAdminProperties properties;
    private final Deque<AlertNotification> recent = new ArrayDeque<>();

    public AlertService(AlertRuleDao ruleDao, List<AlertChannel> channels, BatchAdminProperties properties) {
        this.ruleDao = ruleDao;
        this.properties = properties;
        for (AlertChannel channel : channels) {
            this.channels.putIfAbsent(channel.type(), channel);
        }
    }

    public List<AlertRuleInfo> listRules() {
        return ruleDao.findAll().stream().map(AlertService::toInfo).toList();
    }

    public AlertRuleInfo getRule(long id) {
        return toInfo(require(id));
    }

    public AlertRuleInfo createRule(AlertRuleRequest request) {
        String jobName = request.jobName() == null || request.jobName().isBlank()
                ? "*" : request.jobName().trim();
        AlertRuleType type = AlertRuleType.parse(request.ruleType());
        AlertChannelType channel = AlertChannelType.parse(request.channel());
        Long threshold = request.thresholdMillis();
        if (type == AlertRuleType.DURATION && (threshold == null || threshold <= 0)) {
            throw BatchAdminException.badRequest("A DURATION rule needs a positive 'thresholdMillis'");
        }
        String target = request.target() == null ? null : request.target().trim();
        if (channel == AlertChannelType.WEBHOOK && (target == null || target.isBlank())) {
            throw BatchAdminException.badRequest("A WEBHOOK rule needs a 'target' URL");
        }
        boolean enabled = request.enabled() == null || request.enabled();
        AlertRuleRecord record = ruleDao.insert(jobName, type.name(),
                type == AlertRuleType.DURATION ? threshold : null,
                channel.name(), target, enabled, request.description());
        log.info("[batch-admin] Created alert rule: {} on '{}' via {}", type, jobName, channel);
        return toInfo(record);
    }

    public AlertRuleInfo setEnabled(long id, boolean enabled) {
        require(id);
        ruleDao.setEnabled(id, enabled);
        return toInfo(require(id));
    }

    public void deleteRule(long id) {
        require(id);
        ruleDao.delete(id);
    }

    /** The most recently fired alerts, newest first. */
    public List<AlertNotification> recentAlerts() {
        synchronized (recent) {
            return new ArrayList<>(recent);
        }
    }

    /** Delivers a synthetic alert for a rule, so its channel/target can be checked from the GUI/API. */
    public AlertNotification sendTest(long id) {
        AlertRuleRecord rule = require(id);
        AlertNotification notification = new AlertNotification(rule.jobName(), null, rule.ruleType(),
                "TEST", 0, rule.channel(),
                "Test alert for rule #" + id + " (" + rule.ruleType() + " on '" + rule.jobName() + "')",
                Instant.now());
        dispatch(rule, notification);
        return notification;
    }

    /** Invoked when any administered job finishes; raises alerts for every matching, enabled rule. */
    public void onJobFinished(JobExecution execution) {
        if (!properties.getAlerts().isEnabled()) {
            return;
        }
        String jobName = execution.getJobInstance().getJobName();
        List<AlertRuleRecord> rules = ruleDao.findEnabledForJob(jobName);
        if (rules.isEmpty()) {
            return;
        }
        BatchStatus status = execution.getStatus();
        long durationMs = durationMillis(execution);
        for (AlertRuleRecord rule : rules) {
            AlertRuleType type = AlertRuleType.parse(rule.ruleType());
            String message = evaluate(type, rule, jobName, status, durationMs, execution.getId());
            if (message == null) {
                continue;
            }
            dispatch(rule, new AlertNotification(jobName, execution.getId(), type.name(),
                    status == null ? null : status.toString(), durationMs, rule.channel(), message,
                    Instant.now()));
        }
    }

    private static String evaluate(AlertRuleType type, AlertRuleRecord rule, String jobName,
                                   BatchStatus status, long durationMs, Long executionId) {
        if (type == AlertRuleType.FAILURE) {
            if (status == BatchStatus.FAILED || status == BatchStatus.ABANDONED) {
                return "Job '" + jobName + "' failed (execution " + executionId + ", status " + status + ")";
            }
            return null;
        }
        // DURATION
        long threshold = rule.thresholdMillis() == null ? 0 : rule.thresholdMillis();
        if (durationMs > threshold) {
            return "Job '" + jobName + "' overran its SLA: ran " + durationMs + "ms (threshold "
                    + threshold + "ms), status " + status;
        }
        return null;
    }

    private void dispatch(AlertRuleRecord rule, AlertNotification notification) {
        record(notification);
        AlertChannel channel = channels.get(AlertChannelType.parse(rule.channel()));
        if (channel == null) {
            log.warn("[batch-admin][alert] no channel registered for '{}'; alert only buffered: {}",
                    rule.channel(), notification.message());
            return;
        }
        try {
            channel.send(notification, rule.target());
        } catch (RuntimeException ex) {
            log.warn("[batch-admin][alert] channel '{}' failed: {}", rule.channel(), ex.getMessage());
        }
    }

    private void record(AlertNotification notification) {
        int max = Math.max(1, properties.getAlerts().getRecentBufferSize());
        synchronized (recent) {
            recent.addFirst(notification);
            while (recent.size() > max) {
                recent.removeLast();
            }
        }
    }

    private static long durationMillis(JobExecution execution) {
        LocalDateTime start = execution.getStartTime();
        LocalDateTime end = execution.getEndTime();
        if (start == null || end == null) {
            return 0;
        }
        return Math.max(0, Duration.between(start, end).toMillis());
    }

    private AlertRuleRecord require(long id) {
        return ruleDao.findById(id)
                .orElseThrow(() -> BatchAdminException.notFound("Unknown alert rule: " + id));
    }

    private static AlertRuleInfo toInfo(AlertRuleRecord record) {
        return new AlertRuleInfo(record.id(), record.jobName(), record.ruleType(),
                record.thresholdMillis(), record.channel(), record.target(), record.enabled(),
                record.description(), record.createdAt());
    }
}
