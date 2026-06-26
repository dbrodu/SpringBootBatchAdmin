package io.batchadmin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.batchadmin.autoconfigure.BatchAdminProperties;
import io.batchadmin.domain.JobDefinitionDao;
import io.batchadmin.domain.JobDefinitionRecord;
import io.batchadmin.dynamic.StepDefinition;
import io.batchadmin.dynamic.TaskletProvider;
import io.batchadmin.web.dto.CreateJobRequest;
import io.batchadmin.web.dto.ProviderInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<String, TaskletProvider> providers;
    private final ObjectMapper objectMapper;
    private final BatchAdminProperties properties;
    private final JobExecutionListener jobLogListener;

    public DynamicJobService(JobRegistry jobRegistry,
                             JobRepository jobRepository,
                             PlatformTransactionManager transactionManager,
                             JobDefinitionDao definitionDao,
                             List<TaskletProvider> providers,
                             ObjectMapper objectMapper,
                             BatchAdminProperties properties,
                             JobExecutionListener jobLogListener) {
        this.jobRegistry = jobRegistry;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.definitionDao = definitionDao;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.jobLogListener = jobLogListener;
        Map<String, TaskletProvider> byType = new LinkedHashMap<>();
        for (TaskletProvider provider : providers) {
            byType.put(provider.getType().toLowerCase(), provider);
        }
        this.providers = byType;
    }

    public List<ProviderInfo> listProviders() {
        return providers.values().stream()
                .map(p -> new ProviderInfo(p.getType(), p.getDisplayName(), p.describeProperties()))
                .toList();
    }

    public String createJob(CreateJobRequest request) {
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
        definitionDao.save(name, request.description(), writeSteps(request.steps()));

        log.info("[batch-admin] Created dynamic job '{}' with {} step(s)", name, request.steps().size());
        return name;
    }

    public void deleteJob(String jobName) {
        if (definitionDao.findByJobName(jobName).isEmpty()) {
            throw BatchAdminException.notFound(
                    "No dynamic job named '" + jobName + "' (only dynamic jobs can be deleted)");
        }
        jobRegistry.unregister(jobName);
        definitionDao.deleteByJobName(jobName);
        log.info("[batch-admin] Deleted dynamic job '{}'", jobName);
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

    private void validateSteps(List<StepDefinition> steps) {
        for (StepDefinition step : steps) {
            if (step.name() == null || step.name().isBlank()) {
                throw BatchAdminException.badRequest("Every step needs a name");
            }
            if (!providers.containsKey(step.type() == null ? "" : step.type().toLowerCase())) {
                throw BatchAdminException.badRequest("Unknown step type '" + step.type()
                        + "'. Available types: " + providers.keySet());
            }
        }
    }

    private Job buildJob(String name, List<StepDefinition> steps) {
        JobBuilder jobBuilder = new JobBuilder(name, jobRepository).incrementer(new RunIdIncrementer());
        if (jobLogListener != null) {
            jobBuilder.listener(jobLogListener);
        }
        SimpleJobBuilder simpleBuilder = null;
        for (StepDefinition definition : steps) {
            Step step = buildStep(name, definition);
            simpleBuilder = (simpleBuilder == null) ? jobBuilder.start(step) : simpleBuilder.next(step);
        }
        return simpleBuilder.build();
    }

    private Step buildStep(String jobName, StepDefinition definition) {
        TaskletProvider provider = providers.get(definition.type().toLowerCase());
        Tasklet tasklet = provider.create(definition.properties());
        return new StepBuilder(jobName + "." + definition.name(), jobRepository)
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
