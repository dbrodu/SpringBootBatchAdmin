package io.batchadmin.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.batchadmin.domain.BatchAdminSchemaInitializer;
import io.batchadmin.domain.JobDefinitionDao;
import io.batchadmin.domain.JobScheduleDao;
import io.batchadmin.dynamic.TaskletProvider;
import io.batchadmin.dynamic.provider.CommandTaskletProvider;
import io.batchadmin.dynamic.provider.LoggingTaskletProvider;
import io.batchadmin.dynamic.provider.SleepTaskletProvider;
import io.batchadmin.service.DynamicJobService;
import io.batchadmin.service.JobAdminService;
import io.batchadmin.service.JobSchedulingService;
import io.batchadmin.service.ObservabilityService;
import io.batchadmin.web.BatchAdminExceptionHandler;
import io.batchadmin.web.BatchAdminFormat;
import io.batchadmin.web.BatchAdminViewController;
import io.batchadmin.web.ExecutionController;
import io.batchadmin.web.JobController;
import io.batchadmin.web.ObservabilityController;
import io.batchadmin.web.ScheduleController;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.DuplicateJobException;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.ReferenceJobFactory;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.SimpleJobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Self-initializing auto-configuration for the Spring Batch Admin component.
 *
 * <p>It activates automatically as soon as Spring Batch, a {@code DataSource} and Spring Web are on
 * the classpath, registers the host application's existing {@code Job} beans so they are launchable,
 * and wires the REST API plus the GUI under the configured base path. It stays agnostic of the jobs
 * that already exist and never touches the host application's own JPA/transaction configuration.</p>
 */
@AutoConfiguration(after = {BatchAutoConfiguration.class, DataSourceAutoConfiguration.class,
        JacksonAutoConfiguration.class})
@ConditionalOnClass({Job.class, JobLauncher.class, DataSource.class})
@ConditionalOnBean({JobRepository.class, JobExplorer.class, JobRegistry.class, DataSource.class})
@ConditionalOnProperty(prefix = "batch.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(BatchAdminProperties.class)
public class BatchAdminAutoConfiguration {

    // ----------------------------------------------------------------------------------------
    // Persistence
    // ----------------------------------------------------------------------------------------

    @Bean
    public BatchAdminSchemaInitializer batchAdminSchemaInitializer(DataSource dataSource) {
        BatchAdminSchemaInitializer initializer = new BatchAdminSchemaInitializer(dataSource);
        initializer.initialize();
        return initializer;
    }

    @Bean
    @ConditionalOnMissingBean
    public JobDefinitionDao jobDefinitionDao(DataSource dataSource,
                                             BatchAdminSchemaInitializer schemaInitializer) {
        return new JobDefinitionDao(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public JobScheduleDao jobScheduleDao(DataSource dataSource,
                                         BatchAdminSchemaInitializer schemaInitializer) {
        return new JobScheduleDao(dataSource);
    }

    // ----------------------------------------------------------------------------------------
    // Batch launch / operate infrastructure
    // ----------------------------------------------------------------------------------------

    /** Asynchronous launcher so that "start on the fly" requests return immediately. */
    @Bean(name = "batchAdminJobLauncher")
    @ConditionalOnMissingBean(name = "batchAdminJobLauncher")
    public JobLauncher batchAdminJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("batch-admin-job-"));
        launcher.afterPropertiesSet();
        return launcher;
    }

    @Bean
    @ConditionalOnMissingBean(JobOperator.class)
    public JobOperator batchAdminJobOperator(JobRepository jobRepository,
                                             JobExplorer jobExplorer,
                                             JobRegistry jobRegistry,
                                             @org.springframework.beans.factory.annotation.Qualifier("batchAdminJobLauncher")
                                             JobLauncher jobLauncher) throws Exception {
        SimpleJobOperator operator = new SimpleJobOperator();
        operator.setJobRepository(jobRepository);
        operator.setJobExplorer(jobExplorer);
        operator.setJobRegistry(jobRegistry);
        operator.setJobLauncher(jobLauncher);
        operator.afterPropertiesSet();
        return operator;
    }

    /**
     * Registers the host application's {@code Job} beans into the registry so they are launchable,
     * and attaches the per-execution log listener to them so their logs are captured too.
     */
    @Bean
    public SmartInitializingSingleton batchAdminJobBeanRegistrar(
            JobRegistry jobRegistry,
            ObjectProvider<Job> jobs,
            ObjectProvider<io.batchadmin.logs.JobLogExecutionListener> logListener) {
        io.batchadmin.logs.JobLogExecutionListener listener = logListener.getIfAvailable();
        return () -> jobs.forEach(job -> {
            if (listener != null && job instanceof org.springframework.batch.core.job.AbstractJob abstractJob) {
                // Appends to the job's composite listener (does not replace existing listeners).
                abstractJob.setJobExecutionListeners(
                        new org.springframework.batch.core.JobExecutionListener[]{listener});
            }
            if (!jobRegistry.getJobNames().contains(job.getName())) {
                try {
                    jobRegistry.register(new ReferenceJobFactory(job));
                } catch (DuplicateJobException ignored) {
                    // Already registered by Boot; nothing to do.
                }
            }
        });
    }

    // ----------------------------------------------------------------------------------------
    // Default tasklet providers (host applications contribute their own as beans)
    // ----------------------------------------------------------------------------------------

    @Bean
    public LoggingTaskletProvider loggingTaskletProvider() {
        return new LoggingTaskletProvider();
    }

    @Bean
    public SleepTaskletProvider sleepTaskletProvider(JobExplorer jobExplorer) {
        return new SleepTaskletProvider(jobExplorer);
    }

    @Bean
    @ConditionalOnProperty(prefix = "batch.admin.dynamic-jobs", name = "allow-command-tasklets",
            havingValue = "true")
    public CommandTaskletProvider commandTaskletProvider() {
        return new CommandTaskletProvider();
    }

    /** Composable SQL -> JSON -> target (e.g. OpenSearch) export step, usable from the API and GUI. */
    @Bean
    @ConditionalOnMissingBean(io.batchadmin.dynamic.provider.SqlExportStepProvider.class)
    public io.batchadmin.dynamic.provider.SqlExportStepProvider sqlExportStepProvider(DataSource dataSource) {
        return new io.batchadmin.dynamic.provider.SqlExportStepProvider(dataSource);
    }

    // ----------------------------------------------------------------------------------------
    // Metadata-driven value resolution (SpEL)
    // ----------------------------------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean(io.batchadmin.metadata.MetadataService.class)
    public io.batchadmin.metadata.MetadataService batchAdminMetadataService(BatchAdminProperties properties) {
        return new io.batchadmin.metadata.PropertiesMetadataService(properties.getMetadata());
    }

    @Bean
    @ConditionalOnMissingBean
    public io.batchadmin.metadata.ValueResolver batchAdminValueResolver(
            io.batchadmin.metadata.MetadataService metadataService, BatchAdminProperties properties) {
        return new io.batchadmin.metadata.ValueResolver(metadataService, properties.getExpressions().isEnabled());
    }

    // ----------------------------------------------------------------------------------------
    // Services
    // ----------------------------------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    public JobAdminService jobAdminService(JobRegistry jobRegistry,
                                           JobExplorer jobExplorer,
                                           JobOperator jobOperator,
                                           @org.springframework.beans.factory.annotation.Qualifier("batchAdminJobLauncher")
                                           JobLauncher jobLauncher,
                                           JobDefinitionDao jobDefinitionDao,
                                           io.batchadmin.metadata.ValueResolver valueResolver,
                                           BatchAdminProperties properties) {
        return new JobAdminService(jobRegistry, jobExplorer, jobOperator, jobLauncher,
                jobDefinitionDao, valueResolver, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicJobService dynamicJobService(JobRegistry jobRegistry,
                                               JobRepository jobRepository,
                                               PlatformTransactionManager transactionManager,
                                               JobDefinitionDao jobDefinitionDao,
                                               List<TaskletProvider> providers,
                                               List<io.batchadmin.dynamic.StepProvider> stepProviders,
                                               ObjectMapper objectMapper,
                                               BatchAdminProperties properties,
                                               ObjectProvider<io.batchadmin.logs.JobLogExecutionListener> logListener,
                                               io.batchadmin.metadata.ValueResolver valueResolver) {
        return new DynamicJobService(jobRegistry, jobRepository, transactionManager, jobDefinitionDao,
                providers, stepProviders, objectMapper, properties, logListener.getIfAvailable(),
                valueResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservabilityService observabilityService(JobAdminService jobAdminService,
                                                      JobRegistry jobRegistry,
                                                      JobDefinitionDao jobDefinitionDao,
                                                      JobScheduleDao jobScheduleDao,
                                                      BatchAdminProperties properties) {
        return new ObservabilityService(jobAdminService, jobRegistry, jobDefinitionDao,
                jobScheduleDao, properties);
    }

    // ----------------------------------------------------------------------------------------
    // Startup reload of persisted dynamic jobs and schedules
    // ----------------------------------------------------------------------------------------

    @Bean
    public ApplicationListener<ApplicationReadyEvent> batchAdminStartupReloader(
            DynamicJobService dynamicJobService,
            ObjectProvider<JobSchedulingService> schedulingService) {
        return event -> {
            dynamicJobService.reloadPersistedJobs();
            schedulingService.ifAvailable(JobSchedulingService::reloadSchedules);
        };
    }

    // ----------------------------------------------------------------------------------------
    // Scheduling (optional)
    // ----------------------------------------------------------------------------------------

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "batch.admin.scheduling", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public static class SchedulingConfiguration {

        @Bean(name = "batchAdminTaskScheduler")
        @ConditionalOnMissingBean(name = "batchAdminTaskScheduler")
        public TaskScheduler batchAdminTaskScheduler(BatchAdminProperties properties) {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(properties.getScheduling().getPoolSize());
            scheduler.setThreadNamePrefix("batch-admin-sched-");
            scheduler.setWaitForTasksToCompleteOnShutdown(false);
            scheduler.initialize();
            return scheduler;
        }

        @Bean
        @ConditionalOnMissingBean
        public JobSchedulingService jobSchedulingService(
                @org.springframework.beans.factory.annotation.Qualifier("batchAdminTaskScheduler")
                TaskScheduler taskScheduler,
                JobScheduleDao jobScheduleDao,
                JobAdminService jobAdminService,
                ObjectMapper objectMapper) {
            return new JobSchedulingService(taskScheduler, jobScheduleDao, jobAdminService, objectMapper);
        }

        /**
         * Declared alongside its service (rather than in {@code WebConfiguration}) so the controller
         * is created exactly when scheduling is enabled, avoiding fragile cross-configuration
         * {@code @ConditionalOnBean} ordering.
         */
        @Bean
        @ConditionalOnWebApplication
        public ScheduleController batchAdminScheduleController(JobSchedulingService schedulingService) {
            return new ScheduleController(schedulingService);
        }
    }

    // ----------------------------------------------------------------------------------------
    // Web layer (REST controllers + GUI)
    // ----------------------------------------------------------------------------------------

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication
    public static class WebConfiguration {

        @Bean
        public JobController batchAdminJobController(JobAdminService jobAdminService,
                                                     DynamicJobService dynamicJobService) {
            return new JobController(jobAdminService, dynamicJobService);
        }

        @Bean
        public ExecutionController batchAdminExecutionController(JobAdminService jobAdminService) {
            return new ExecutionController(jobAdminService);
        }

        @Bean
        public ObservabilityController batchAdminObservabilityController(
                ObservabilityService observabilityService) {
            return new ObservabilityController(observabilityService);
        }

        @Bean
        public BatchAdminExceptionHandler batchAdminExceptionHandler() {
            return new BatchAdminExceptionHandler();
        }

        @Bean
        @ConditionalOnProperty(prefix = "batch.admin", name = "ui-enabled", havingValue = "true",
                matchIfMissing = true)
        public BatchAdminFormat batchAdminFormat() {
            return new BatchAdminFormat();
        }

        @Bean
        @ConditionalOnProperty(prefix = "batch.admin", name = "ui-enabled", havingValue = "true",
                matchIfMissing = true)
        public BatchAdminViewController batchAdminViewController(
                JobAdminService jobAdminService,
                DynamicJobService dynamicJobService,
                ObservabilityService observabilityService,
                org.springframework.beans.factory.ObjectProvider<JobSchedulingService> schedulingService,
                org.springframework.beans.factory.ObjectProvider<io.batchadmin.service.JobLogService> jobLogService,
                BatchAdminProperties properties) {
            return new BatchAdminViewController(jobAdminService, dynamicJobService, observabilityService,
                    schedulingService, jobLogService, properties);
        }
    }

    // ----------------------------------------------------------------------------------------
    // Per-execution log capture (optional; requires Logback)
    // ----------------------------------------------------------------------------------------

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
    @ConditionalOnProperty(prefix = "batch.admin.logs", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public static class LoggingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.batchadmin.logs.BatchAdminLogStore batchAdminLogStore(BatchAdminProperties properties) {
            return new io.batchadmin.logs.BatchAdminLogStore(
                    properties.getLogs().getMaxRecordsPerExecution(),
                    properties.getLogs().getMaxExecutions());
        }

        @Bean
        public io.batchadmin.logs.JobLogExecutionListener batchAdminJobLogListener() {
            return new io.batchadmin.logs.JobLogExecutionListener();
        }

        @Bean
        public io.batchadmin.logs.BatchAdminLogbackInitializer batchAdminLogbackInitializer(
                io.batchadmin.logs.BatchAdminLogStore store, BatchAdminProperties properties) {
            return new io.batchadmin.logs.BatchAdminLogbackInitializer(store, properties);
        }

        @Bean
        @ConditionalOnMissingBean
        public io.batchadmin.service.JobLogService jobLogService(
                io.batchadmin.logs.BatchAdminLogStore store,
                JobAdminService jobAdminService,
                BatchAdminProperties properties) {
            return new io.batchadmin.service.JobLogService(store, jobAdminService, properties);
        }

        @Bean
        @ConditionalOnWebApplication
        public io.batchadmin.web.JobLogController batchAdminJobLogController(
                io.batchadmin.service.JobLogService jobLogService) {
            return new io.batchadmin.web.JobLogController(jobLogService);
        }
    }

    // ----------------------------------------------------------------------------------------
    // Observability metrics (optional)
    // ----------------------------------------------------------------------------------------

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "batch.admin.observability", name = "metrics-enabled",
            havingValue = "true", matchIfMissing = true)
    public static class MetricsConfiguration {

        @Bean
        public MeterBinder batchAdminMeterBinder(ObservabilityService observabilityService) {
            return (MeterRegistry registry) -> {
                Gauge.builder("batch.admin.executions.running", observabilityService,
                                ObservabilityService::runningExecutions)
                        .description("Number of currently running batch executions")
                        .register(registry);
                Gauge.builder("batch.admin.schedules.active", observabilityService,
                                ObservabilityService::activeSchedules)
                        .description("Number of enabled cron schedules")
                        .register(registry);
            };
        }
    }
}
