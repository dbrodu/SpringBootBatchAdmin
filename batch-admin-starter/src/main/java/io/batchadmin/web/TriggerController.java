package io.batchadmin.web;

import io.batchadmin.service.JobTriggerService;
import io.batchadmin.web.dto.JobGraph;
import io.batchadmin.web.dto.JobTriggerInfo;
import io.batchadmin.web.dto.JobTriggerRequest;
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
 * REST endpoints to manage job triggers (event-driven chaining / pipelines).
 */
@RestController
@RequestMapping("${batch.admin.base-path:/batch-admin}/api/triggers")
public class TriggerController {

    private final JobTriggerService triggerService;

    public TriggerController(JobTriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @GetMapping
    public List<JobTriggerInfo> list() {
        return triggerService.listTriggers();
    }

    /** The trigger graph (jobs as nodes, triggers as edges), laid out for drawing. */
    @GetMapping("/graph")
    public JobGraph graph() {
        return triggerService.buildGraph();
    }

    @GetMapping("/{id}")
    public JobTriggerInfo get(@PathVariable long id) {
        return triggerService.getTrigger(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobTriggerInfo create(@RequestBody JobTriggerRequest request) {
        return triggerService.createTrigger(request);
    }

    @PutMapping("/{id}/enabled")
    public JobTriggerInfo setEnabled(@PathVariable long id, @RequestParam boolean value) {
        return triggerService.setEnabled(id, value);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        triggerService.deleteTrigger(id);
    }
}
