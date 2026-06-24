package io.batchadmin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.batchadmin.domain.JobScheduleDao;
import io.batchadmin.domain.JobScheduleRecord;
import io.batchadmin.schedule.NaturalCronParser;
import io.batchadmin.web.dto.ScheduleInfo;
import io.batchadmin.web.dto.ScheduleRequest;
import io.batchadmin.web.dto.StartJobRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;

/**
 * Manages per-job cron schedules: persistence, (re)arming against a {@link TaskScheduler} and
 * computation of the next fire time. Enabled schedules are re-armed when the application starts.
 */
public class JobSchedulingService {

    private static final Logger log = LoggerFactory.getLogger(JobSchedulingService.class);

    private final TaskScheduler taskScheduler;
    private final JobScheduleDao scheduleDao;
    private final JobAdminService jobAdminService;
    private final ObjectMapper objectMapper;
    private final NaturalCronParser cronParser = new NaturalCronParser();
    private final Map<Long, ScheduledFuture<?>> armed = new ConcurrentHashMap<>();

    public JobSchedulingService(TaskScheduler taskScheduler,
                                JobScheduleDao scheduleDao,
                                JobAdminService jobAdminService,
                                ObjectMapper objectMapper) {
        this.taskScheduler = taskScheduler;
        this.scheduleDao = scheduleDao;
        this.jobAdminService = jobAdminService;
        this.objectMapper = objectMapper;
    }

    public List<ScheduleInfo> listSchedules() {
        return scheduleDao.findAll().stream().map(this::toInfo).toList();
    }

    public ScheduleInfo getSchedule(long id) {
        return toInfo(require(id));
    }

    public ScheduleInfo createSchedule(ScheduleRequest request) {
        String cron = validateAndResolveCron(request);
        JobScheduleRecord record = scheduleDao.insert(
                request.jobName().trim(),
                cron,
                writeParameters(request.parameters()),
                request.enabled() == null || request.enabled(),
                request.description());
        arm(record);
        log.info("[batch-admin] Scheduled job '{}' with cron '{}'", record.jobName(), record.cron());
        return toInfo(record);
    }

    public ScheduleInfo updateSchedule(long id, ScheduleRequest request) {
        String cron = validateAndResolveCron(request);
        JobScheduleRecord existing = require(id);
        boolean enabled = request.enabled() != null ? request.enabled() : existing.enabled();
        scheduleDao.update(
                id,
                request.jobName().trim(),
                cron,
                writeParameters(request.parameters()),
                enabled,
                request.description());
        return toInfo(require(id));
    }

    public ScheduleInfo setEnabled(long id, boolean enabled) {
        require(id);
        scheduleDao.setEnabled(id, enabled);
        return toInfo(require(id));
    }

    public void deleteSchedule(long id) {
        JobScheduleRecord record = require(id);
        cancel(id);
        scheduleDao.delete(id);
        log.info("[batch-admin] Removed schedule {} for job '{}'", id, record.jobName());
    }

    /** Arms every enabled schedule. Invoked once the application is ready. */
    public void reloadSchedules() {
        for (JobScheduleRecord record : scheduleDao.findEnabled()) {
            arm(record);
        }
    }

    // ----------------------------------------------------------------------------------------
    // Internals
    // ----------------------------------------------------------------------------------------

    private synchronized void arm(JobScheduleRecord record) {
        cancel(record.id());
        if (!record.enabled()) {
            return;
        }
        Map<String, String> parameters = readParameters(record.parametersJson());
        String jobName = record.jobName();
        Runnable task = () -> {
            try {
                jobAdminService.startJob(jobName, new StartJobRequest(parameters));
            } catch (RuntimeException ex) {
                log.error("[batch-admin] Scheduled launch of '{}' failed: {}", jobName, ex.getMessage());
            }
        };
        ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(record.cron()));
        if (future != null) {
            armed.put(record.id(), future);
        }
    }

    private void cancel(Long id) {
        if (id == null) {
            return;
        }
        ScheduledFuture<?> future = armed.remove(id);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * Validates the request and resolves its frequency to a cron expression. The frequency may be a
     * raw cron expression, a Spring macro ({@code @daily}, …) or a human-readable phrase such as
     * "tous les jours à 2h30", which is converted via {@link NaturalCronParser}.
     */
    private String validateAndResolveCron(ScheduleRequest request) {
        if (request.jobName() == null || request.jobName().isBlank()) {
            throw BatchAdminException.badRequest("'jobName' is required");
        }
        // Ensure the job is known so we fail fast on typos.
        jobAdminService.getJob(request.jobName().trim());
        try {
            return cronParser.toCron(request.cron());
        } catch (IllegalArgumentException ex) {
            throw BatchAdminException.badRequest(ex.getMessage());
        }
    }

    private JobScheduleRecord require(long id) {
        return scheduleDao.findById(id)
                .orElseThrow(() -> BatchAdminException.notFound("Unknown schedule: " + id));
    }

    private ScheduleInfo toInfo(JobScheduleRecord record) {
        Instant next = null;
        if (record.enabled() && CronExpression.isValidExpression(record.cron())) {
            LocalDateTime nextDateTime = CronExpression.parse(record.cron()).next(LocalDateTime.now());
            if (nextDateTime != null) {
                next = nextDateTime.atZone(ZoneId.systemDefault()).toInstant();
            }
        }
        return new ScheduleInfo(
                record.id(),
                record.jobName(),
                record.cron(),
                record.description(),
                record.enabled(),
                readParameters(record.parametersJson()),
                next,
                record.createdAt());
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
