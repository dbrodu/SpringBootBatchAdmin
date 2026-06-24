package io.batchadmin.service;

import io.batchadmin.autoconfigure.BatchAdminProperties;
import io.batchadmin.domain.JobDefinitionDao;
import io.batchadmin.domain.JobScheduleDao;
import io.batchadmin.web.dto.ExecutionSummary;
import io.batchadmin.web.dto.ObservabilitySummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.batch.core.configuration.JobRegistry;

/**
 * Produces aggregated, dashboard-oriented views over the batch activity. Per-execution timing
 * remains available through Micrometer (Spring Batch publishes {@code spring.batch.job} timers
 * automatically when actuator + micrometer are present).
 */
public class ObservabilityService {

    private final JobAdminService jobAdminService;
    private final JobRegistry jobRegistry;
    private final JobDefinitionDao definitionDao;
    private final JobScheduleDao scheduleDao;
    private final BatchAdminProperties properties;

    public ObservabilityService(JobAdminService jobAdminService,
                                JobRegistry jobRegistry,
                                JobDefinitionDao definitionDao,
                                JobScheduleDao scheduleDao,
                                BatchAdminProperties properties) {
        this.jobAdminService = jobAdminService;
        this.jobRegistry = jobRegistry;
        this.definitionDao = definitionDao;
        this.scheduleDao = scheduleDao;
        this.properties = properties;
    }

    public ObservabilitySummary summary() {
        int limit = properties.getObservability().getRecentExecutionsLimit();
        List<ExecutionSummary> recent = jobAdminService.recentExecutions(limit);

        Map<String, Long> statusCounts = recent.stream()
                .collect(Collectors.groupingBy(ExecutionSummary::status, LinkedHashMap::new, Collectors.counting()));

        int totalJobs = jobRegistry.getJobNames().size();
        int dynamicJobs = (int) definitionDao.count();
        int activeSchedules = scheduleDao.findEnabled().size();
        int running = jobAdminService.countRunningExecutions();

        return new ObservabilitySummary(totalJobs, dynamicJobs, activeSchedules, running, statusCounts, recent);
    }

    /** Convenience map of last execution status per job, used by the GUI job board. */
    public Map<String, String> lastStatusByJob() {
        return jobAdminService.listJobs().stream()
                .filter(job -> job.lastStatus() != null)
                .collect(Collectors.toMap(
                        io.batchadmin.web.dto.JobSummary::name,
                        io.batchadmin.web.dto.JobSummary::lastStatus,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    public int runningExecutions() {
        return jobAdminService.countRunningExecutions();
    }

    public int activeSchedules() {
        return scheduleDao.findEnabled().size();
    }
}
