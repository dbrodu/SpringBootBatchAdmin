package io.batchadmin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.batchadmin.autoconfigure.BatchAdminProperties;
import io.batchadmin.domain.JobDefinitionDao;
import io.batchadmin.domain.JobDefinitionRecord;
import io.batchadmin.domain.JobDefinitionVersionDao;
import io.batchadmin.domain.JobDefinitionVersionRecord;
import io.batchadmin.dynamic.ExistingStepCatalog;
import io.batchadmin.dynamic.StepDefinition;
import io.batchadmin.dynamic.StepProvider;
import io.batchadmin.dynamic.TaskletProvider;
import io.batchadmin.metadata.ValueResolver;
import io.batchadmin.web.dto.CreateJobRequest;
import io.batchadmin.web.dto.ImportResult;
import io.batchadmin.web.dto.JobPreview;
import io.batchadmin.web.dto.JobStepPreview;
import io.batchadmin.web.dto.JobVersionInfo;
import io.batchadmin.web.dto.ProviderInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.DuplicateJobException;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.ReferenceJobFactory;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.SimpleJobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Builds Spring Batch jobs at runtime from declarative definitions, registers them into the
 * {@link JobRegistry} so they become launchable, and persists them so they survive restarts.
 *
 * <p>Steps are materialized through {@link TaskletProvider} beans, which lets host applications
 * expose their own building blocks without this component knowing anything about them.</p>
 */
public class DynamicJobService {

    private static final Logger log = LoggerFactory.getLogger(DynamicJobService.class);

    private final JobRegistry jobRegistry;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobDefinitionDao definitionDao;
    private final JobDefinitionVersionDao versionDao;
    private final Map<String, TaskletProvider> providers;
    private final Map<String, StepProvider> stepProviders;
    private final ObjectMapper objectMapper;
    private final BatchAdminProperties properties;
    private final List<JobExecutionListener> componentListeners;
    private final ValueResolver valueResolver;
    private final ExistingStepCatalog existingStepCatalog;

    public DynamicJobService(JobRegistry jobRegistry,
                             JobRepository jobRepository,
                             PlatformTransactionManager transactionManager,
                             JobDefinitionDao definitionDao,
                             JobDefinitionVersionDao versionDao,
                             List<TaskletProvider> providers,
                             List<StepProvider> stepProviders,
                             ObjectMapper objectMapper,
                             BatchAdminProperties properties,
                             List<JobExecutionListener> componentListeners,
                             ValueResolver valueResolver,
                             ExistingStepCatalog existingStepCatalog) {
        this.jobRegistry = jobRegistry;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.definitionDao = definitionDao;
        this.versionDao = versionDao;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.componentListeners = componentListeners == null ? List.of() : componentListeners;
        this.valueResolver = valueResolver;
        this.existingStepCatalog = existingStepCatalog;
        Map<String, TaskletProvider> byType = new LinkedHashMap<>();
        for (TaskletProvider provider : providers) {
            byType.put(provider.getType().toLowerCase(), provider);
        }
        this.providers = byType;
        Map<String, StepProvider> stepByType = new LinkedHashMap<>();
        for (StepProvider provider : stepProviders) {
            stepByType.put(provider.getType().toLowerCase(), provider);
        }
        this.stepProviders = stepByType;
    }

    public List<ProviderInfo> listProviders() {
        return providers.values().stream()
                .map(p -> new ProviderInfo(p.getType(), p.getDisplayName(), p.describeProperties()))
                .toList();
    }

    /** Step providers (chunk-oriented building blocks such as {@code sql-export}). */
    public List<ProviderInfo> listStepProviders() {
        return stepProviders.values().stream()
                .map(p -> new ProviderInfo(p.getType(), p.getDisplayName(), p.describeProperties()))
                .toList();
    }

    /**
     * Building blocks <b>derived from the host's existing jobs</b>: each reusable step is offered as a
     * step type that drops the application's own step straight into a new on-the-fly job. Empty when
     * {@code batch.admin.dynamic-jobs.reuse-existing-steps=false}.
     */
    public List<ProviderInfo> listReusableSteps() {
        if (!properties.getDynamicJobs().isReuseExistingSteps()) {
            return List.of();
        }
        return existingStepCatalog.reusableSteps().stream()
                .map(s -> new ProviderInfo(s.type(),
                        "Reuse step '" + s.step().getName() + "' from job '" + s.jobName() + "'",
                        Map.of()))
                .toList();
    }

    /**
     * Building blocks that reuse a whole existing job's flow (all of its steps, in order) as a single
     * block. Empty when {@code batch.admin.dynamic-jobs.reuse-existing-steps=false}.
     */
    public List<ProviderInfo> listReusableJobs() {
        if (!properties.getDynamicJobs().isReuseExistingSteps()) {
            return List.of();
        }
        return existingStepCatalog.reusableJobs().stream()
                .map(j -> new ProviderInfo(j.type(),
                        "Reuse job '" + j.jobName() + "' (" + j.steps().size() + " step"
                                + (j.steps().size() == 1 ? "" : "s") + ", in order)",
                        Map.of()))
                .toList();
    }

    public String createJob(CreateJobRequest request) {
        return createJob(request, null);
    }

    /** Creates a dynamic job, recording the first version with the given audit note. */
    public String createJob(CreateJobRequest request, String changeNote) {
        return createJobInternal(request, VersionChangeType.CREATE, changeNote);
    }

    private String createJobInternal(CreateJobRequest request, VersionChangeType changeType, String changeNote) {
        if (!properties.getDynamicJobs().isEnabled()) {
            throw BatchAdminException.badRequest("Dynamic job creation is disabled");
        }
        String name = request.jobName() != null ? request.jobName().trim() : "";
        if (name.isBlank()) {
            throw BatchAdminException.badRequest("'jobName' is required");
        }
        if (request.steps().isEmpty()) {
            throw BatchAdminException.badRequest("A job needs at least one step");
        }
        if (jobRegistry.getJobNames().contains(name)) {
            throw BatchAdminException.conflict("A job named '" + name + "' already exists");
        }
        validateSteps(request.steps());

        Job job = buildJob(name, request.steps());
        registerJob(job);
        String stepsJson = writeSteps(request.steps());
        definitionDao.save(name, request.description(), stepsJson);
        recordVersion(name, request.description(), stepsJson, changeType, changeNote);

        log.info("[batch-admin] Created dynamic job '{}' with {} step(s)", name, request.steps().size());
        return name;
    }

    /** Appends a snapshot of the job's current definition, with audit metadata, to its history. */
    private void recordVersion(String jobName, String description, String stepsJson,
                               VersionChangeType changeType, String changeNote) {
        versionDao.save(jobName, versionDao.nextVersion(jobName), description, stepsJson,
                CurrentActor.name(), changeType.name(), trimToNull(changeNote));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Dry-run a composition: validates it and returns the ordered, fully expanded list of steps the
     * job <i>would</i> run — {@code job:<name>} whole-job blocks expanded into their constituent
     * steps — without building, registering or persisting anything.
     */
    public JobPreview previewJob(CreateJobRequest request) {
        if (request.steps().isEmpty()) {
            throw BatchAdminException.badRequest("A job needs at least one step");
        }
        validateSteps(request.steps());
        String jobName = request.jobName() == null ? "" : request.jobName().trim();
        String prefix = jobName.isBlank() ? "" : jobName + ".";

        List<JobStepPreview> resolved = new ArrayList<>();
        for (StepDefinition definition : request.steps()) {
            String type = definition.type() == null ? "" : definition.type().toLowerCase();
            if (reuseExistingSteps() && existingStepCatalog.containsJob(type)) {
                String source = definition.type().substring(ExistingStepCatalog.JOB_TYPE_PREFIX.length());
                for (Step step : existingStepCatalog.findJobSteps(type)) {
                    resolved.add(new JobStepPreview(step.getName(), definition.type(),
                            "reused from job '" + source + "'"));
                }
            } else if (reuseExistingSteps() && existingStepCatalog.contains(type)) {
                Step step = existingStepCatalog.find(type);
                resolved.add(new JobStepPreview(step.getName(), definition.type(), "reused existing step"));
            } else if (stepProviders.containsKey(type)) {
                resolved.add(new JobStepPreview(prefix + definition.name(), definition.type(), "step provider"));
            } else {
                resolved.add(new JobStepPreview(prefix + definition.name(), definition.type(), "tasklet"));
            }
        }
        return new JobPreview(jobName, resolved.size(), resolved);
    }

    /**
     * Creates a new dynamic job that replicates an existing one. A dynamic source is copied
     * definition-for-definition (an exact clone); a declared host job is cloned by reusing its whole
     * flow ({@code job:<name>}), since its original definitions are not known to the component.
     *
     * @param sourceName     the job to clone (declared or dynamic)
     * @param requestedName  name for the clone; when blank, {@code <source>-copy} is used
     * @return the name of the created clone
     */
    public String cloneJob(String sourceName, String requestedName) {
        if (!jobRegistry.getJobNames().contains(sourceName)) {
            throw BatchAdminException.notFound("No job named '" + sourceName + "'");
        }
        String newName = (requestedName == null || requestedName.isBlank())
                ? sourceName + "-copy" : requestedName.trim();

        List<StepDefinition> steps;
        Optional<JobDefinitionRecord> definition = definitionDao.findByJobName(sourceName);
        if (definition.isPresent()) {
            steps = readSteps(definition.get().stepsJson());
        } else {
            if (!reuseExistingSteps()) {
                throw BatchAdminException.badRequest("Cloning the declared job '" + sourceName
                        + "' requires reuse of existing steps to be enabled");
            }
            if (!existingStepCatalog.containsJob(ExistingStepCatalog.JOB_TYPE_PREFIX + sourceName)) {
                throw BatchAdminException.badRequest("Job '" + sourceName
                        + "' does not expose its steps and cannot be cloned");
            }
            steps = List.of(new StepDefinition(sourceName,
                    ExistingStepCatalog.JOB_TYPE_PREFIX + sourceName, Map.of()));
        }
        return createJob(new CreateJobRequest(newName, "Clone of '" + sourceName + "'", steps),
                "Cloned from '" + sourceName + "'");
    }

    /** The stored definition (name, description, steps) of a dynamic job, for editing/inspection. */
    public CreateJobRequest getDefinition(String jobName) {
        JobDefinitionRecord record = definitionDao.findByJobName(jobName).orElseThrow(() ->
                BatchAdminException.notFound("No dynamic job named '" + jobName + "'"));
        return new CreateJobRequest(record.jobName(), record.description(), readSteps(record.stepsJson()));
    }

    /**
     * Replaces the steps/description of an existing <b>dynamic</b> job in place (the name is fixed).
     * The new composition is built and validated <i>before</i> the old job is swapped out, so a bad
     * edit leaves the current job untouched.
     */
    public String updateJob(String jobName, CreateJobRequest request) {
        return updateJob(jobName, request, null);
    }

    /** Edits a dynamic job in place, recording the new version with the given audit note. */
    public String updateJob(String jobName, CreateJobRequest request, String changeNote) {
        return applyUpdate(jobName, request, VersionChangeType.EDIT, changeNote);
    }

    private String applyUpdate(String jobName, CreateJobRequest request,
                               VersionChangeType changeType, String changeNote) {
        if (!properties.getDynamicJobs().isEnabled()) {
            throw BatchAdminException.badRequest("Dynamic job creation is disabled");
        }
        if (definitionDao.findByJobName(jobName).isEmpty()) {
            throw BatchAdminException.notFound(
                    "No dynamic job named '" + jobName + "' (only dynamic jobs can be edited)");
        }
        if (request.steps().isEmpty()) {
            throw BatchAdminException.badRequest("A job needs at least one step");
        }
        validateSteps(request.steps());

        Job rebuilt = buildJob(jobName, request.steps());   // build first: bad edits never drop the job
        jobRegistry.unregister(jobName);
        registerJob(rebuilt);
        String stepsJson = writeSteps(request.steps());
        definitionDao.update(jobName, request.description(), stepsJson);
        recordVersion(jobName, request.description(), stepsJson, changeType, changeNote);

        log.info("[batch-admin] Updated dynamic job '{}' with {} step(s)", jobName, request.steps().size());
        return jobName;
    }

    public void deleteJob(String jobName) {
        if (definitionDao.findByJobName(jobName).isEmpty()) {
            throw BatchAdminException.notFound(
                    "No dynamic job named '" + jobName + "' (only dynamic jobs can be deleted)");
        }
        jobRegistry.unregister(jobName);
        definitionDao.deleteByJobName(jobName);
        versionDao.deleteByJobName(jobName);
        log.info("[batch-admin] Deleted dynamic job '{}'", jobName);
    }

    // ----------------------------------------------------------------------------------------
    // Version history & rollback
    // ----------------------------------------------------------------------------------------

    /** A dynamic job's version history, newest first; the highest version is the current one. */
    public List<JobVersionInfo> listVersions(String jobName) {
        if (definitionDao.findByJobName(jobName).isEmpty()) {
            throw BatchAdminException.notFound("No dynamic job named '" + jobName + "'");
        }
        List<JobDefinitionVersionRecord> records = versionDao.findByJobName(jobName);
        int current = records.stream().mapToInt(JobDefinitionVersionRecord::version).max().orElse(0);
        return records.stream()
                .map(r -> new JobVersionInfo(r.version(), r.description(), readSteps(r.stepsJson()),
                        r.author(), r.changeType(), r.changeNote(),
                        r.createdAt(), r.version() == current))
                .toList();
    }

    /**
     * Restores a dynamic job to a previous version. The old definition is rebuilt and validated, then
     * swapped in, and the restored content is appended as a <b>new</b> version (history is never
     * rewritten), so a rollback can itself be rolled back.
     */
    public String rollbackJob(String jobName, int version) {
        if (definitionDao.findByJobName(jobName).isEmpty()) {
            throw BatchAdminException.notFound(
                    "No dynamic job named '" + jobName + "' (only dynamic jobs have versions)");
        }
        JobDefinitionVersionRecord target = versionDao.find(jobName, version).orElseThrow(() ->
                BatchAdminException.notFound("Job '" + jobName + "' has no version " + version));
        List<StepDefinition> steps = readSteps(target.stepsJson());
        return applyUpdate(jobName, new CreateJobRequest(jobName, target.description(), steps),
                VersionChangeType.ROLLBACK, "Rolled back to version " + version);
    }

    // ----------------------------------------------------------------------------------------
    // Export / import (portable JSON definitions, to move jobs between environments)
    // ----------------------------------------------------------------------------------------

    /** Every dynamic job's portable definition (name, description, steps), in id order. */
    public List<CreateJobRequest> exportAll() {
        return definitionDao.findAll().stream()
                .map(record -> new CreateJobRequest(record.jobName(), record.description(),
                        readSteps(record.stepsJson())))
                .toList();
    }

    /** One dynamic job's portable definition. */
    public CreateJobRequest exportJob(String jobName) {
        JobDefinitionRecord record = definitionDao.findByJobName(jobName).orElseThrow(() ->
                BatchAdminException.notFound("No dynamic job named '" + jobName + "'"));
        return new CreateJobRequest(record.jobName(), record.description(), readSteps(record.stepsJson()));
    }

    /** All dynamic jobs serialized as a pretty-printed JSON array (for download). */
    public String exportAllJson() {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportAll());
        } catch (Exception ex) {
            throw new BatchAdminException(BatchAdminException.Kind.INTERNAL, "Cannot serialize jobs", ex);
        }
    }

    /**
     * Imports job definitions previously exported (e.g. from another environment). Each definition is
     * created; one whose name already exists as a dynamic job is overwritten when {@code overwrite} is
     * set, otherwise skipped. A name colliding with a declared host job, or any definition that fails
     * to build, is reported as failed; the rest still import.
     */
    public ImportResult importJobs(List<CreateJobRequest> definitions, boolean overwrite) {
        if (!properties.getDynamicJobs().isEnabled()) {
            throw BatchAdminException.badRequest("Dynamic job creation is disabled");
        }
        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<String, String> failed = new LinkedHashMap<>();
        for (CreateJobRequest definition : definitions) {
            String name = definition.jobName() == null ? "" : definition.jobName().trim();
            try {
                if (name.isBlank()) {
                    throw BatchAdminException.badRequest("'jobName' is required");
                }
                boolean existsDynamic = definitionDao.findByJobName(name).isPresent();
                if (existsDynamic) {
                    if (overwrite) {
                        // Validate the replacement builds before removing the existing job.
                        validateSteps(definition.steps());
                        buildJob(name, definition.steps());
                        deleteJob(name);
                        createJobInternal(definition, VersionChangeType.IMPORT, "Imported from JSON");
                        updated.add(name);
                    } else {
                        skipped.add(name);
                    }
                } else if (jobRegistry.getJobNames().contains(name)) {
                    failed.put(name, "a declared (non-dynamic) job with this name already exists");
                } else {
                    created.add(createJobInternal(definition, VersionChangeType.IMPORT, "Imported from JSON"));
                }
            } catch (RuntimeException ex) {
                failed.put(name.isBlank() ? "(unnamed)" : name, ex.getMessage());
            }
        }
        log.info("[batch-admin] Imported job definitions: {} created, {} updated, {} skipped, {} failed",
                created.size(), updated.size(), skipped.size(), failed.size());
        return new ImportResult(created, updated, skipped, failed);
    }

    /** Parses an exported JSON array and {@link #importJobs(List, boolean) imports} it. */
    public ImportResult importJobsFromJson(String json, boolean overwrite) {
        if (json == null || json.isBlank()) {
            throw BatchAdminException.badRequest("No JSON to import");
        }
        List<CreateJobRequest> definitions;
        try {
            definitions = objectMapper.readValue(json, new TypeReference<List<CreateJobRequest>>() {
            });
        } catch (Exception ex) {
            throw BatchAdminException.badRequest("Invalid job-definitions JSON: " + ex.getMessage());
        }
        return importJobs(definitions, overwrite);
    }

    /** Re-registers every persisted dynamic job. Invoked once the application is ready. */
    public void reloadPersistedJobs() {
        for (JobDefinitionRecord record : definitionDao.findAll()) {
            if (jobRegistry.getJobNames().contains(record.jobName())) {
                continue;
            }
            try {
                List<StepDefinition> steps = readSteps(record.stepsJson());
                registerJob(buildJob(record.jobName(), steps));
                // Backfill a baseline version for jobs persisted before version history existed.
                if (!versionDao.existsByJobName(record.jobName())) {
                    recordVersion(record.jobName(), record.description(), record.stepsJson(),
                            VersionChangeType.BASELINE, "Baseline (created before version history)");
                }
                log.info("[batch-admin] Re-registered persisted dynamic job '{}'", record.jobName());
            } catch (RuntimeException ex) {
                log.warn("[batch-admin] Could not re-register dynamic job '{}': {}",
                        record.jobName(), ex.getMessage());
            }
        }
    }

    // ----------------------------------------------------------------------------------------
    // Internals
    // ----------------------------------------------------------------------------------------

    private boolean reuseExistingSteps() {
        return properties.getDynamicJobs().isReuseExistingSteps();
    }

    private void validateSteps(List<StepDefinition> steps) {
        for (StepDefinition step : steps) {
            if (step.name() == null || step.name().isBlank()) {
                throw BatchAdminException.badRequest("Every step needs a name");
            }
            String type = step.type() == null ? "" : step.type().toLowerCase();
            boolean known = providers.containsKey(type) || stepProviders.containsKey(type)
                    || (reuseExistingSteps()
                        && (existingStepCatalog.contains(type) || existingStepCatalog.containsJob(type)));
            if (!known) {
                Set<String> available = new java.util.TreeSet<>(providers.keySet());
                available.addAll(stepProviders.keySet());
                if (reuseExistingSteps()) {
                    existingStepCatalog.reusableSteps().forEach(s -> available.add(s.type()));
                    existingStepCatalog.reusableJobs().forEach(j -> available.add(j.type()));
                }
                throw BatchAdminException.badRequest("Unknown step type '" + step.type()
                        + "'. Available types: " + available);
            }
        }
    }

    private Job buildJob(String name, List<StepDefinition> steps) {
        JobBuilder jobBuilder = new JobBuilder(name, jobRepository).incrementer(new RunIdIncrementer());
        for (JobExecutionListener listener : componentListeners) {
            if (listener != null) {
                jobBuilder.listener(listener);
            }
        }
        SimpleJobBuilder simpleBuilder = null;
        for (StepDefinition definition : steps) {
            for (Step step : buildSteps(name, definition)) {
                simpleBuilder = (simpleBuilder == null) ? jobBuilder.start(step) : simpleBuilder.next(step);
            }
        }
        if (simpleBuilder == null) {
            throw BatchAdminException.badRequest("A job needs at least one step");
        }
        return simpleBuilder.build();
    }

    /**
     * Materializes one step definition into the step(s) it contributes: a single step for ordinary
     * blocks, or — when the type is a {@code job:<name>} whole-job block — that job's entire ordered
     * flow reused as-is.
     */
    private List<Step> buildSteps(String jobName, StepDefinition definition) {
        String type = definition.type() == null ? "" : definition.type().toLowerCase();
        if (reuseExistingSteps()) {
            List<Step> flow = existingStepCatalog.findJobSteps(type);
            if (flow != null) {
                if (flow.isEmpty()) {
                    throw BatchAdminException.badRequest("Reused job '" + definition.type() + "' has no steps");
                }
                return flow;
            }
        }
        return List.of(buildStep(jobName, definition));
    }

    private Step buildStep(String jobName, StepDefinition definition) {
        String stepName = jobName + "." + definition.name();
        String type = definition.type().toLowerCase();
        // Resolve any SpEL / metadata expression in the step properties (e.g. an index name from
        // metadata) once, for both chunk-oriented step providers and tasklet providers.
        Map<String, Object> properties = valueResolver.resolveProperties(definition.properties());

        StepProvider stepProvider = stepProviders.get(type);
        if (stepProvider != null) {
            try {
                return stepProvider.buildStep(stepName, properties,
                        new StepProvider.Context(jobRepository, transactionManager));
            } catch (IllegalArgumentException ex) {
                throw BatchAdminException.badRequest(ex.getMessage());
            }
        }

        if (reuseExistingSteps()) {
            // A step derived from an existing job: reuse that step instance as-is (it keeps its own
            // name, and the same step may legitimately belong to more than one job).
            Step existing = existingStepCatalog.find(type);
            if (existing != null) {
                return existing;
            }
        }

        TaskletProvider provider = providers.get(type);
        Tasklet tasklet = provider.create(properties);
        return new StepBuilder(stepName, jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    private void registerJob(Job job) {
        try {
            jobRegistry.register(new ReferenceJobFactory(job));
        } catch (DuplicateJobException ex) {
            throw BatchAdminException.conflict("A job named '" + job.getName() + "' already exists");
        }
    }

    private String writeSteps(List<StepDefinition> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (Exception ex) {
            throw new BatchAdminException(BatchAdminException.Kind.INTERNAL, "Cannot serialize steps", ex);
        }
    }

    private List<StepDefinition> readSteps(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<StepDefinition>>() {
            });
        } catch (Exception ex) {
            throw new BatchAdminException(BatchAdminException.Kind.INTERNAL, "Cannot deserialize steps", ex);
        }
    }
}
