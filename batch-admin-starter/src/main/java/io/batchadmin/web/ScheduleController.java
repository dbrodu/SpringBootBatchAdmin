package io.batchadmin.web;

import io.batchadmin.service.JobSchedulingService;
import io.batchadmin.web.dto.ScheduleInfo;
import io.batchadmin.web.dto.ScheduleRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints to manage per-job cron schedules.
 */
@RestController
@RequestMapping("${batch.admin.base-path:/batch-admin}/api/schedules")
public class ScheduleController {

    private final JobSchedulingService schedulingService;

    public ScheduleController(JobSchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @GetMapping
    public List<ScheduleInfo> list() {
        return schedulingService.listSchedules();
    }

    @GetMapping("/{id}")
    public ScheduleInfo get(@PathVariable long id) {
        return schedulingService.getSchedule(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleInfo create(@RequestBody ScheduleRequest request) {
        return schedulingService.createSchedule(request);
    }

    @PutMapping("/{id}")
    public ScheduleInfo update(@PathVariable long id, @RequestBody ScheduleRequest request) {
        return schedulingService.updateSchedule(id, request);
    }

    @PutMapping("/{id}/enabled")
    public ScheduleInfo setEnabled(@PathVariable long id, @RequestParam boolean value) {
        return schedulingService.setEnabled(id, value);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        schedulingService.deleteSchedule(id);
    }
}
