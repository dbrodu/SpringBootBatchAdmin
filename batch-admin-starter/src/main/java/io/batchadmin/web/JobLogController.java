package io.batchadmin.web;

import io.batchadmin.logs.JobLogEntry;
import io.batchadmin.service.JobLogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints to read the captured execution logs with a configurable minimum level.
 */
@RestController
@RequestMapping("${batch.admin.base-path:/batch-admin}/api")
public class JobLogController {

    private final JobLogService jobLogService;

    public JobLogController(JobLogService jobLogService) {
        this.jobLogService = jobLogService;
    }

    @GetMapping("/executions/{executionId}/logs")
    public List<JobLogEntry> logs(@PathVariable long executionId,
                                  @RequestParam(required = false) String level,
                                  @RequestParam(defaultValue = "1000") int limit) {
        return jobLogService.read(executionId, level, limit);
    }

    @GetMapping("/log-levels")
    public List<String> levels() {
        return jobLogService.levels();
    }
}
