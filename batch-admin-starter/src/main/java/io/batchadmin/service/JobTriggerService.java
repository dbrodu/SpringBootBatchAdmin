package io.batchadmin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.batchadmin.autoconfigure.BatchAdminProperties;
import io.batchadmin.domain.JobTriggerDao;
import io.batchadmin.domain.JobTriggerRecord;
import io.batchadmin.web.dto.JobGraph;
import io.batchadmin.web.dto.JobTriggerInfo;
import io.batchadmin.web.dto.JobTriggerRequest;
import io.batchadmin.web.dto.StartJobRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;

/**
 * Event-driven job chaining ("pipelines"): persisted {@link JobTriggerRecord rules} that launch a
 * target job when a source job finishes matching a {@link TriggerCondition condition}. Chains
 * (A→B→C) and fan-out (A→B, A→C) fall out naturally because every launched job is itself observed.
 *
 * <p>A trigger can forward the source job's parameters to the target and/or add static ones, so a
 * pipeline can pass context downstream. A {@code chainDepth} job parameter is threaded through each
 * launch and capped ({@code batch.admin.triggers.max-chain-depth}) so cyclic or runaway chains
 * terminate.</p>
 */
public class JobTriggerService {

    private static final Logger log = LoggerFactory.getLogger(JobTriggerService.class);

    /** Identifying job parameters threaded through a chain so depth can be bounded and shown. */
    public static final String CHAIN_DEPTH_PARAM = "batchAdmin.chainDepth";
    public static final String CHAIN_SOURCE_PARAM = "batchAdmin.chainSource";

    private final JobTriggerDao triggerDao;
    private final JobAdminService jobAdminService;
    private final ObjectMapper objectMapper;
    private final BatchAdminProperties properties;

    public JobTriggerService(JobTriggerDao triggerDao, JobAdminService jobAdminService,
                             ObjectMapper objectMapper, BatchAdminProperties properties) {
        this.triggerDao = triggerDao;
        this.jobAdminService = jobAdminService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<JobTriggerInfo> listTriggers() {
        return triggerDao.findAll().stream().map(this::toInfo).toList();
    }

    public JobTriggerInfo getTrigger(long id) {
        return toInfo(require(id));
    }

    /**
     * Lays out the trigger graph (jobs as nodes, triggers as directed edges) with a simple
     * longest-path layering, so it can be drawn as an SVG. Cycle-safe: the relaxation is bounded by
     * the node count.
     */
    public JobGraph buildGraph() {
        List<JobTriggerRecord> triggers = triggerDao.findAll();
        LinkedHashSet<String> nodeNames = new LinkedHashSet<>();
        for (JobTriggerRecord trigger : triggers) {
            nodeNames.add(trigger.sourceJob());
            nodeNames.add(trigger.targetJob());
        }

        Map<String, Integer> level = new HashMap<>();
        for (String name : nodeNames) {
            level.put(name, 0);
        }
        for (int iteration = 0; iteration < nodeNames.size(); iteration++) {
            boolean changed = false;
            for (JobTriggerRecord trigger : triggers) {
                int candidate = level.get(trigger.sourceJob()) + 1;
                if (candidate > level.get(trigger.targetJob())) {
                    level.put(trigger.targetJob(), candidate);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        Map<Integer, List<String>> byLevel = new TreeMap<>();
        for (String name : nodeNames) {
            byLevel.computeIfAbsent(level.get(name), key -> new ArrayList<>()).add(name);
        }
        byLevel.values().forEach(column -> column.sort(Comparator.naturalOrder()));

        int nodeW = 170;
        int nodeH = 42;
        int pitchX = nodeW + 80;
        int pitchY = nodeH + 30;
        int margin = 24;
        Map<String, JobGraph.Node> nodes = new LinkedHashMap<>();
        int maxRows = 0;
        for (Map.Entry<Integer, List<String>> entry : byLevel.entrySet()) {
            List<String> column = entry.getValue();
            maxRows = Math.max(maxRows, column.size());
            for (int row = 0; row < column.size(); row++) {
                String name = column.get(row);
                int x = margin + entry.getKey() * pitchX;
                int y = margin + row * pitchY;
                nodes.put(name, new JobGraph.Node(name, truncate(name, 22), x, y, nodeW, nodeH));
            }
        }

        List<JobGraph.Edge> edges = new ArrayList<>();
        for (JobTriggerRecord trigger : triggers) {
            JobGraph.Node source = nodes.get(trigger.sourceJob());
            JobGraph.Node target = nodes.get(trigger.targetJob());
            if (source == null || target == null) {
                continue;
            }
            int x1 = source.x() + source.w();
            int y1 = source.y() + source.h() / 2;
            int x2 = target.x();
            int y2 = target.y() + target.h() / 2;
            edges.add(new JobGraph.Edge(trigger.sourceJob(), trigger.targetJob(), trigger.condition(),
                    trigger.enabled(), x1, y1, x2, y2, (x1 + x2) / 2, (y1 + y2) / 2 - 6));
        }

        int levels = byLevel.isEmpty() ? 0 : Collections.max(byLevel.keySet()) + 1;
        int width = Math.max(220, margin * 2 + (levels == 0 ? 0 : (levels - 1) * pitchX + nodeW));
        int height = Math.max(90, margin * 2 + (maxRows == 0 ? 0 : (maxRows - 1) * pitchY + nodeH));
        return new JobGraph(width, height, new ArrayList<>(nodes.values()), edges);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
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
        boolean inheritParams = request.inheritParams() != null && request.inheritParams();
        JobTriggerRecord record = triggerDao.insert(source, target, condition.name(), enabled,
                inheritParams, writeParameters(request.parameters()), request.description());
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
     * trigger whose source is this job, forwarding parameters (when configured) and threading (and
     * bounding) the chain depth.
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
        JobParameters sourceParameters = execution.getJobParameters();
        long depth = chainDepth(sourceParameters);
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
            launch(trigger, source, sourceParameters, depth + 1, status);
        }
    }

    private void launch(JobTriggerRecord trigger, String source, JobParameters sourceParameters,
                        long depth, BatchStatus status) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (trigger.inheritParams()) {
            parameters.putAll(forwardableParams(sourceParameters));
        }
        // Static trigger parameters override inherited ones on a key clash.
        parameters.putAll(readParameters(trigger.parametersJson()));
        // Chain bookkeeping is always authoritative.
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

    /** The source job's parameters minus the launcher's {@code run.id} and the chain bookkeeping. */
    private static Map<String, String> forwardableParams(JobParameters parameters) {
        Map<String, String> out = new LinkedHashMap<>();
        if (parameters == null) {
            return out;
        }
        for (Map.Entry<String, JobParameter<?>> entry : parameters.getParameters().entrySet()) {
            String key = entry.getKey();
            if (key.equals("run.id") || key.startsWith("batchAdmin.")) {
                continue;
            }
            Object value = entry.getValue().getValue();
            out.put(key, value == null ? "" : String.valueOf(value));
        }
        return out;
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

    private JobTriggerInfo toInfo(JobTriggerRecord record) {
        return new JobTriggerInfo(record.id(), record.sourceJob(), record.targetJob(),
                record.condition(), record.enabled(), record.inheritParams(),
                readParameters(record.parametersJson()), record.description(), record.createdAt());
    }

    private String writeParameters(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (Exception ex) {
            throw new BatchAdminException(BatchAdminException.Kind.INTERNAL, "Cannot serialize parameters", ex);
        }
    }

    private Map<String, String> readParameters(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
