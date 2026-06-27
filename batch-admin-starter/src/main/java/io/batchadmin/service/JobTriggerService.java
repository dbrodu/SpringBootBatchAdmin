package io.batchadmin.service;

import io.batchadmin.autoconfigure.BatchAdminProperties;
import io.batchadmin.domain.JobTriggerDao;
import io.batchadmin.domain.JobTriggerRecord;
import io.batchadmin.web.dto.JobTriggerInfo;
import io.batchadmin.web.dto.JobTriggerRequest;
import io.batchadmin.web.dto.StartJobRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;

/**
 * Event-driven job chaining ("pipelines"): persisted {@link JobTriggerRecord rules} that launch a
 * target job when a source job finishes matching a {@link TriggerCondition condition}. Chains
 * (A→B→C) and fan-out (A→B, A→C) fall out naturally because every launched job is itself observed.
 *
 * <p>A {@code chainDepth} job parameter is threaded through each launch and capped
 * ({@code batch.admin.triggers.max-chain-depth}) so cyclic or runaway chains terminate.</p>
 */
public class JobTriggerService {

    private static final Logger log = LoggerFactory.getLogger(JobTriggerService.class);

    /** Identifying job parameters threaded through a chain so depth can be bounded and shown. */
    public static final String CHAIN_DEPTH_PARAM = "batchAdmin.chainDepth";
    public static final String CHAIN_SOURCE_PARAM = "batchAdmin.chainSource";

    private final JobTriggerDao triggerDao;
    private final JobAdminService jobAdminService;
    private final BatchAdminProperties properties;

    public JobTriggerService(JobTriggerDao triggerDao, JobAdminService jobAdminService,
                             BatchAdminProperties properties) {
        this.triggerDao = triggerDao;
        this.jobAdminService = jobAdminService;
        this.properties = properties;
    }

    public List<JobTriggerInfo> listTriggers() {
        return triggerDao.findAll().stream().map(JobTriggerService::toInfo).toList();
    }

    public JobTriggerInfo getTrigger(long id) {
        return toInfo(require(id));
    }

    public JobTriggerInfo createTrigger(JobTriggerRequest request) {
        if (!properties.getTriggers().isEnabled()) {
            throw BatchAdminException.badRequest("Job triggers are disabled");
        }
        String source = request.sourceJob() == null ? "" : request.sourceJob().trim();
        String target = request.targetJob() == null ? "" : request.targetJob().trim();
        if (source.isBlank() || target.isBlank()) {
            throw BatchAdminException.badRequest("'sourceJob' and 'targetJob' are required");
        }
        if (source.equals(target)) {
            throw BatchAdminException.badRequest(
                    "A job cannot trigger itself; use a schedule to repeat a job");
        }
        // Fail fast on typos: both jobs must be known to the component.
        jobAdminService.getJob(source);
        jobAdminService.getJob(target);
        TriggerCondition condition = TriggerCondition.parse(request.condition());
        boolean enabled = request.enabled() == null || request.enabled();
        JobTriggerRecord record = triggerDao.insert(source, target, condition.name(), enabled,
                request.description());
        log.info("[batch-admin] Created trigger: '{}' ({}) -> '{}'", source, condition, target);
        return toInfo(record);
    }

    public JobTriggerInfo setEnabled(long id, boolean enabled) {
        require(id);
        triggerDao.setEnabled(id, enabled);
        return toInfo(require(id));
    }

    public void deleteTrigger(long id) {
        JobTriggerRecord record = require(id);
        triggerDao.delete(id);
        log.info("[batch-admin] Removed trigger {} ('{}' -> '{}')", id, record.sourceJob(), record.targetJob());
    }

    /**
     * Invoked when any administered job finishes. Launches the target of every enabled, matching
     * trigger whose source is this job, threading (and bounding) the chain depth.
     */
    public void onJobFinished(JobExecution execution) {
        if (!properties.getTriggers().isEnabled()) {
            return;
        }
        String source = execution.getJobInstance().getJobName();
        BatchStatus status = execution.getStatus();
        List<JobTriggerRecord> triggers = triggerDao.findEnabledBySource(source);
        if (triggers.isEmpty()) {
            return;
        }
        long depth = chainDepth(execution.getJobParameters());
        int maxDepth = properties.getTriggers().getMaxChainDepth();
        for (JobTriggerRecord trigger : triggers) {
            if (!TriggerCondition.parse(trigger.condition()).matches(status)) {
                continue;
            }
            if (depth >= maxDepth) {
                log.warn("[batch-admin] Trigger '{}' -> '{}' skipped: chain depth {} reached the cap {}",
                        source, trigger.targetJob(), depth, maxDepth);
                continue;
            }
            launch(trigger, source, depth + 1, status);
        }
    }

    private void launch(JobTriggerRecord trigger, String source, long depth, BatchStatus status) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(CHAIN_DEPTH_PARAM, String.valueOf(depth));
        parameters.put(CHAIN_SOURCE_PARAM, source);
        try {
            jobAdminService.startJob(trigger.targetJob(), new StartJobRequest(parameters));
            log.info("[batch-admin] Trigger fired: '{}' ({}) -> launched '{}'",
                    source, status, trigger.targetJob());
        } catch (RuntimeException ex) {
            log.error("[batch-admin] Trigger '{}' -> '{}' could not launch: {}",
                    source, trigger.targetJob(), ex.getMessage());
        }
    }

    private static long chainDepth(JobParameters parameters) {
        if (parameters == null) {
            return 0;
        }
        String value = parameters.getString(CHAIN_DEPTH_PARAM);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private JobTriggerRecord require(long id) {
        return triggerDao.findById(id)
                .orElseThrow(() -> BatchAdminException.notFound("Unknown trigger: " + id));
    }

    private static JobTriggerInfo toInfo(JobTriggerRecord record) {
        return new JobTriggerInfo(record.id(), record.sourceJob(), record.targetJob(),
                record.condition(), record.enabled(), record.description(), record.createdAt());
    }
}
