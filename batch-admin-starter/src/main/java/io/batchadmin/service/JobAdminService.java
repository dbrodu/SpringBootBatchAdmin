package io.batchadmin.service;

import io.batchadmin.autoconfigure.BatchAdminProperties;
import io.batchadmin.domain.JobDefinitionDao;
import io.batchadmin.metadata.ValueResolver;
import io.batchadmin.web.dto.ExecutionSummary;
import io.batchadmin.web.dto.JobSummary;
import io.batchadmin.web.dto.StartJobRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;

/**
 * Central service for administering jobs: discovery, launch, stop, restart and history.
 *
 * <p>It works against whatever jobs are registered in the {@link JobRegistry} (whether declared as
 * Spring beans by the host application or created on the fly by this component) and is therefore
 * agnostic of the jobs that already exist.</p>
 */
public class JobAdminService {

    private final JobRegistry jobRegistry;
    private final JobExplorer jobExplorer;
    private final JobOperator jobOperator;
    private final JobLauncher jobLauncher;
    private final JobDefinitionDao jobDefinitionDao;
    private final ValueResolver valueResolver;
    private final BatchAdminProperties properties;

    public JobAdminService(JobRegistry jobRegistry,
                           JobExplorer jobExplorer,
                           JobOperator jobOperator,
                           JobLauncher jobLauncher,
                           JobDefinitionDao jobDefinitionDao,
                           ValueResolver valueResolver,
                           BatchAdminProperties properties) {
        this.jobRegistry = jobRegistry;
        this.jobExplorer = jobExplorer;
        this.jobOperator = jobOperator;
        this.jobLauncher = jobLauncher;
        this.jobDefinitionDao = jobDefinitionDao;
        this.valueResolver = valueResolver;
        this.properties = properties;
    }

    // ----------------------------------------------------------------------------------------
    // Discovery
    // ----------------------------------------------------------------------------------------

    public List<JobSummary> listJobs() {
        Set<String> names = new TreeSet<>();
        names.addAll(jobRegistry.getJobNames());
        names.addAll(jobExplorer.getJobNames());
        return names.stream().map(this::toJobSummary).toList();
    }

    public JobSummary getJob(String jobName) {
        if (!jobRegistry.getJobNames().contains(jobName) && !jobExplorer.getJobNames().contains(jobName)) {
            throw BatchAdminException.notFound("Unknown job: " + jobName);
        }
        return toJobSummary(jobName);
    }

    private JobSummary toJobSummary(String jobName) {
        boolean launchable = jobRegistry.getJobNames().contains(jobName);
        boolean dynamic = jobDefinitionDao.existsByJobName(jobName);
        long instanceCount = safeInstanceCount(jobName);
        boolean running = !jobExplorer.findRunningJobExecutions(jobName).isEmpty();
        JobExecution last = latestExecution(jobName);
        String lastStatus = last != null ? last.getStatus().name() : null;
        Instant lastTime = last != null
                ? BatchAdminMapper.toInstant(last.getStartTime() != null ? last.getStartTime() : last.getCreateTime())
                : null;
        return new JobSummary(jobName, dynamic, launchable, instanceCount, running, lastStatus, lastTime);
    }

    // ----------------------------------------------------------------------------------------
    // Execution history
    // ----------------------------------------------------------------------------------------

    public List<ExecutionSummary> listExecutions(String jobName, int limit) {
        if (!jobExplorer.getJobNames().contains(jobName) && !jobRegistry.getJobNames().contains(jobName)) {
            throw BatchAdminException.notFound("Unknown job: " + jobName);
        }
        return jobExplorer.getJobInstances(jobName, 0, Math.max(1, limit)).stream()
                .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
                .sorted(Comparator.comparingLong(JobExecution::getId).reversed())
                .limit(limit)
                .map(execution -> BatchAdminMapper.toExecutionSummary(execution, false))
                .toList();
    }

    public List<ExecutionSummary> recentExecutions(int limit) {
        Set<String> names = new TreeSet<>();
        names.addAll(jobRegistry.getJobNames());
        names.addAll(jobExplorer.getJobNames());
        return names.stream()
                .flatMap(name -> jobExplorer.getJobInstances(name, 0, limit).stream())
                .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
                .sorted(Comparator.comparingLong(JobExecution::getId).reversed())
                .limit(limit)
                .map(execution -> BatchAdminMapper.toExecutionSummary(execution, false))
                .toList();
    }

    public ExecutionSummary getExecution(long executionId) {
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null) {
            throw BatchAdminException.notFound("Unknown execution: " + executionId);
        }
        return BatchAdminMapper.toExecutionSummary(execution, true);
    }

    // ----------------------------------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------------------------------

    /** Launches a job on the fly. The launcher is asynchronous so the call returns immediately. */
    public ExecutionSummary startJob(String jobName, StartJobRequest request) {
        Job job = resolveJob(jobName);
        JobParameters parameters = buildParameters(request);
        try {
            JobExecution execution = jobLauncher.run(job, parameters);
            return BatchAdminMapper.toExecutionSummary(execution, true);
        } catch (JobExecutionAlreadyRunningException ex) {
            throw BatchAdminException.conflict("An execution of '" + jobName + "' is already running");
        } catch (JobInstanceAlreadyCompleteException ex) {
            throw BatchAdminException.conflict("This job instance has already completed for the given parameters");
        } catch (JobRestartException ex) {
            throw BatchAdminException.conflict("Job cannot be restarted: " + ex.getMessage());
        } catch (JobParametersInvalidException ex) {
            throw BatchAdminException.badRequest("Invalid job parameters: " + ex.getMessage());
        }
    }

    public ExecutionSummary stop(long executionId) {
        try {
            jobOperator.stop(executionId);
        } catch (NoSuchJobExecutionException ex) {
            throw BatchAdminException.notFound("Unknown execution: " + executionId);
        } catch (JobExecutionNotRunningException ex) {
            throw BatchAdminException.conflict("Execution " + executionId + " is not running");
        }
        return getExecution(executionId);
    }

    public ExecutionSummary restart(long executionId) {
        try {
            long newExecutionId = jobOperator.restart(executionId);
            return getExecution(newExecutionId);
        } catch (NoSuchJobExecutionException ex) {
            throw BatchAdminException.notFound("Unknown execution: " + executionId);
        } catch (NoSuchJobException ex) {
            throw BatchAdminException.conflict("Job is no longer registered and cannot be restarted");
        } catch (JobInstanceAlreadyCompleteException ex) {
            throw BatchAdminException.conflict("This job instance has already completed and cannot be restarted");
        } catch (JobRestartException ex) {
            throw BatchAdminException.conflict("Job cannot be restarted: " + ex.getMessage());
        } catch (JobParametersInvalidException ex) {
            throw BatchAdminException.badRequest("Invalid job parameters: " + ex.getMessage());
        }
    }

    public ExecutionSummary abandon(long executionId) {
        try {
            jobOperator.abandon(executionId);
        } catch (NoSuchJobExecutionException ex) {
            throw BatchAdminException.notFound("Unknown execution: " + executionId);
        } catch (JobExecutionAlreadyRunningException ex) {
            throw BatchAdminException.conflict("Execution " + executionId + " is still running; stop it first");
        }
        return getExecution(executionId);
    }

    // ----------------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------------

    private Job resolveJob(String jobName) {
        try {
            return jobRegistry.getJob(jobName);
        } catch (NoSuchJobException ex) {
            throw BatchAdminException.notFound("Job '" + jobName + "' is not registered");
        }
    }

    private JobParameters buildParameters(StartJobRequest request) {
        JobParametersBuilder builder = new JobParametersBuilder();
        Map<String, String> params = request != null ? request.parameters() : Map.of();
        // Resolve any SpEL / metadata expression (e.g. #{metadata.get('region')}, #{today}).
        params = valueResolver.resolveAll(params);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.addString(entry.getKey(), entry.getValue(), true);
        }
        // Guarantee a unique, identifying parameter so manual launches always create a new instance.
        if (!params.containsKey("run.id")) {
            builder.addLong("run.id", System.currentTimeMillis(), true);
        }
        return builder.toJobParameters();
    }

    private long safeInstanceCount(String jobName) {
        try {
            return jobExplorer.getJobInstanceCount(jobName);
        } catch (NoSuchJobException ex) {
            return 0L;
        }
    }

    private JobExecution latestExecution(String jobName) {
        List<JobInstance> instances = jobExplorer.getJobInstances(jobName, 0, 1);
        if (instances.isEmpty()) {
            return null;
        }
        return jobExplorer.getJobExecutions(instances.get(0)).stream()
                .max(Comparator.comparingLong(JobExecution::getId))
                .orElse(null);
    }

    /** Count of currently running executions across all known jobs (used for observability). */
    public int countRunningExecutions() {
        Set<String> names = new TreeSet<>();
        names.addAll(jobRegistry.getJobNames());
        names.addAll(jobExplorer.getJobNames());
        return (int) names.stream()
                .flatMap(name -> jobExplorer.findRunningJobExecutions(name).stream())
                .filter(execution -> execution.getStatus() == BatchStatus.STARTED
                        || execution.getStatus() == BatchStatus.STARTING
                        || execution.getStatus() == BatchStatus.STOPPING)
                .count();
    }

    public BatchAdminProperties getProperties() {
        return properties;
    }
}
