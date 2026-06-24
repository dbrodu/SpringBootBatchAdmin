package io.batchadmin.web;

import io.batchadmin.service.ObservabilityService;
import io.batchadmin.web.dto.ObservabilitySummary;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints exposing the aggregated observability surface needed to run the jobs.
 */
@RestController
@RequestMapping("${batch.admin.base-path:/batch-admin}/api/observability")
public class ObservabilityController {

    private final ObservabilityService observabilityService;

    public ObservabilityController(ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    @GetMapping("/summary")
    public ObservabilitySummary summary() {
        return observabilityService.summary();
    }

    @GetMapping("/last-status")
    public Map<String, String> lastStatusByJob() {
        return observabilityService.lastStatusByJob();
    }
}
