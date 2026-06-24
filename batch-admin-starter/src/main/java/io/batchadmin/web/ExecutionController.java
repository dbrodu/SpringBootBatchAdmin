package io.batchadmin.web;

import io.batchadmin.service.JobAdminService;
import io.batchadmin.web.dto.ExecutionSummary;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints to observe and control individual job executions.
 */
@RestController
@RequestMapping("${batch.admin.base-path:/batch-admin}/api/executions")
public class ExecutionController {

    private final JobAdminService jobAdminService;

    public ExecutionController(JobAdminService jobAdminService) {
        this.jobAdminService = jobAdminService;
    }

    @GetMapping
    public List<ExecutionSummary> recent(@RequestParam(defaultValue = "50") int limit) {
        return jobAdminService.recentExecutions(limit);
    }

    @GetMapping("/{executionId}")
    public ExecutionSummary get(@PathVariable long executionId) {
        return jobAdminService.getExecution(executionId);
    }

    @PostMapping("/{executionId}/stop")
    public ExecutionSummary stop(@PathVariable long executionId) {
        return jobAdminService.stop(executionId);
    }

    @PostMapping("/{executionId}/restart")
    public ExecutionSummary restart(@PathVariable long executionId) {
        return jobAdminService.restart(executionId);
    }

    @PostMapping("/{executionId}/abandon")
    public ExecutionSummary abandon(@PathVariable long executionId) {
        return jobAdminService.abandon(executionId);
    }
}
