package io.batchadmin.web;

import io.batchadmin.service.AlertService;
import io.batchadmin.web.dto.AlertNotification;
import io.batchadmin.web.dto.AlertRuleInfo;
import io.batchadmin.web.dto.AlertRuleRequest;
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
 * REST endpoints to manage alert rules (failure / SLA) and read recently fired alerts.
 */
@RestController
@RequestMapping("${batch.admin.base-path:/batch-admin}/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertRuleInfo> list() {
        return alertService.listRules();
    }

    /** The most recently fired alerts (newest first). Declared before {@code /{id}}. */
    @GetMapping("/recent")
    public List<AlertNotification> recent() {
        return alertService.recentAlerts();
    }

    @GetMapping("/{id}")
    public AlertRuleInfo get(@PathVariable long id) {
        return alertService.getRule(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertRuleInfo create(@RequestBody AlertRuleRequest request) {
        return alertService.createRule(request);
    }

    @PutMapping("/{id}/enabled")
    public AlertRuleInfo setEnabled(@PathVariable long id, @RequestParam boolean value) {
        return alertService.setEnabled(id, value);
    }

    /** Sends a synthetic alert through the rule's channel, to verify its configuration. */
    @PostMapping("/{id}/test")
    public AlertNotification test(@PathVariable long id) {
        return alertService.sendTest(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        alertService.deleteRule(id);
    }
}
