package io.batchadmin.web;

import io.batchadmin.autoconfigure.BatchAdminProperties;
import io.batchadmin.dynamic.StepDefinition;
import io.batchadmin.service.BatchAdminException;
import io.batchadmin.service.DynamicJobService;
import io.batchadmin.service.JobAdminService;
import io.batchadmin.service.JobLogService;
import io.batchadmin.service.JobSchedulingService;
import io.batchadmin.service.ObservabilityService;
import io.batchadmin.web.dto.CreateJobRequest;
import io.batchadmin.web.dto.ImportResult;
import io.batchadmin.web.dto.JobPreview;
import io.batchadmin.web.dto.JobVersionInfo;
import io.batchadmin.web.dto.ScheduleInfo;
import io.batchadmin.web.dto.ScheduleRequest;
import io.batchadmin.web.dto.StartJobRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Server-rendered Thymeleaf GUI for the admin component. Everything is produced by Spring Boot
 * itself — there is no separate front-end to build or deploy. Actions are plain HTML form POSTs
 * that redirect back (Post/Redirect/Get) so the GUI works without any client-side framework.
 */
@Controller
@RequestMapping("${batch.admin.base-path:/batch-admin}")
public class BatchAdminViewController {

    private static final Pattern STEP_LINE =
            Pattern.compile("^\\s*([^=]+?)\\s*=\\s*([^\\s(]+)\\s*(?:\\((.*)\\))?\\s*$");

    private final JobAdminService jobAdminService;
    private final DynamicJobService dynamicJobService;
    private final ObservabilityService observabilityService;
    private final ObjectProvider<JobSchedulingService> schedulingService;
    private final ObjectProvider<JobLogService> jobLogService;
    private final String basePath;

    public BatchAdminViewController(JobAdminService jobAdminService,
                                    DynamicJobService dynamicJobService,
                                    ObservabilityService observabilityService,
                                    ObjectProvider<JobSchedulingService> schedulingService,
                                    ObjectProvider<JobLogService> jobLogService,
                                    BatchAdminProperties properties) {
        this.jobAdminService = jobAdminService;
        this.dynamicJobService = dynamicJobService;
        this.observabilityService = observabilityService;
        this.schedulingService = schedulingService;
        this.jobLogService = jobLogService;
        this.basePath = properties.getBasePath();
    }

    @ModelAttribute("basePath")
    public String basePath() {
        return basePath;
    }

    @ModelAttribute("schedulingEnabled")
    public boolean schedulingEnabled() {
        return schedulingService.getIfAvailable() != null;
    }

    // ----------------------------------------------------------------------------------------
    // Pages
    // ----------------------------------------------------------------------------------------

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("active", "dashboard");
        model.addAttribute("refresh", 5);
        model.addAttribute("summary", observabilityService.summary());
        return "batch-admin/dashboard";
    }

    @GetMapping("/jobs")
    public String jobs(Model model) {
        model.addAttribute("active", "jobs");
        model.addAttribute("jobs", jobAdminService.listJobs());
        model.addAttribute("nextRuns", nextRunByJob());
        return "batch-admin/jobs";
    }

    /** For each job, the schedule that fires soonest (enabled, earliest next run), for the Jobs grid. */
    private Map<String, ScheduleInfo> nextRunByJob() {
        JobSchedulingService scheduling = schedulingService.getIfAvailable();
        if (scheduling == null) {
            return Map.of();
        }
        Map<String, ScheduleInfo> soonest = new LinkedHashMap<>();
        for (ScheduleInfo schedule : scheduling.listSchedules()) {
            ScheduleInfo current = soonest.get(schedule.jobName());
            if (current == null || firesSooner(schedule, current)) {
                soonest.put(schedule.jobName(), schedule);
            }
        }
        return soonest;
    }

    private static boolean firesSooner(ScheduleInfo candidate, ScheduleInfo current) {
        java.time.Instant c = candidate.enabled() ? candidate.nextExecution() : null;
        java.time.Instant e = current.enabled() ? current.nextExecution() : null;
        if (c == null) {
            return false;
        }
        return e == null || c.isBefore(e);
    }

    @GetMapping("/jobs/new")
    public String newJob(Model model) {
        return renderCreateJob(model, "", "", "", null, null);
    }

    /** Loads an existing dynamic job into the composer for editing. */
    @GetMapping("/jobs/{jobName}/edit")
    public String editJob(@PathVariable String jobName, Model model, RedirectAttributes redirect) {
        try {
            CreateJobRequest definition = dynamicJobService.getDefinition(jobName);
            return renderCreateJob(model, definition.jobName(),
                    definition.description() == null ? "" : definition.description(),
                    stepsToText(definition.steps()), null, jobName);
        } catch (BatchAdminException ex) {
            flash(redirect, true, ex.getMessage());
            return redirect(redirect, "/jobs");
        }
    }

    @PostMapping("/jobs/{jobName}/edit")
    public String updateJob(@PathVariable String jobName,
                            @RequestParam(required = false) String description,
                            @RequestParam(required = false) String steps,
                            @RequestParam(required = false) String note,
                            RedirectAttributes redirect) {
        try {
            List<StepDefinition> stepDefinitions = parseSteps(steps);
            dynamicJobService.updateJob(jobName, new CreateJobRequest(jobName, description, stepDefinitions), note);
            flash(redirect, false, "Updated job '" + jobName + "'.");
            return redirect(redirect, "/jobs");
        } catch (BatchAdminException | IllegalArgumentException ex) {
            flash(redirect, true, ex.getMessage());
            return redirect(redirect, "/jobs/" + jobName + "/edit");
        }
    }

    /** Dry-run the composition and re-render the form with the expanded steps shown. */
    @PostMapping("/jobs/preview")
    public String previewJob(@RequestParam(required = false) String jobName,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) String steps,
                             @RequestParam(required = false) String editJobName,
                             Model model, RedirectAttributes redirect) {
        try {
            List<StepDefinition> stepDefinitions = parseSteps(steps);
            JobPreview preview = dynamicJobService.previewJob(
                    new CreateJobRequest(jobName, description, stepDefinitions));
            return renderCreateJob(model, jobName, description, steps, preview, editJobName);
        } catch (BatchAdminException | IllegalArgumentException ex) {
            flash(redirect, true, ex.getMessage());
            return redirect(redirect, editJobName == null || editJobName.isBlank()
                    ? "/jobs/new" : "/jobs/" + editJobName + "/edit");
        }
    }

    private String renderCreateJob(Model model, String jobName, String description, String steps,
                                   JobPreview preview, String editJobName) {
        model.addAttribute("active", "create");
        model.addAttribute("providers", dynamicJobService.listProviders());
        model.addAttribute("stepProviders", dynamicJobService.listStepProviders());
        model.addAttribute("reusableSteps", dynamicJobService.listReusableSteps());
        model.addAttribute("reusableJobs", dynamicJobService.listReusableJobs());
        model.addAttribute("formJobName", jobName == null ? "" : jobName);
        model.addAttribute("formDescription", description == null ? "" : description);
        model.addAttribute("formSteps", steps == null ? "" : steps);
        model.addAttribute("preview", preview);
        model.addAttribute("editJobName", editJobName == null || editJobName.isBlank() ? null : editJobName);
        return "batch-admin/create-job";
    }

    /** Renders a job's stored step definitions back into the textarea grammar ({@code name = type (k=v)}). */
    static String stepsToText(List<StepDefinition> steps) {
        StringBuilder text = new StringBuilder();
        for (StepDefinition step : steps) {
            text.append(step.name()).append(" = ").append(step.type());
            if (step.properties() != null && !step.properties().isEmpty()) {
                String props = step.properties().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(java.util.stream.Collectors.joining(", "));
                text.append(" (").append(props).append(")");
            }
            text.append("\n");
        }
        return text.toString();
    }

    @GetMapping("/executions")
    public String executions(@RequestParam(name = "selected", required = false) Long selected,
                             @RequestParam(name = "logLevel", required = false) String logLevel,
                             Model model) {
        model.addAttribute("active", "executions");
        model.addAttribute("refresh", 5);
        model.addAttribute("executions", jobAdminService.recentExecutions(50));
        JobLogService logs = jobLogService.getIfAvailable();
        model.addAttribute("logsEnabled", logs != null);
        if (selected != null) {
            model.addAttribute("detail", jobAdminService.getExecution(selected));
            if (logs != null) {
                String level = (logLevel == null || logLevel.isBlank()) ? "INFO" : logLevel;
                model.addAttribute("logLevel", level);
                model.addAttribute("logLevels", logs.levels());
                model.addAttribute("logs", logs.read(selected, level, 1000));
            }
        }
        return "batch-admin/executions";
    }

    @GetMapping("/schedules")
    public String schedules(Model model) {
        model.addAttribute("active", "schedules");
        JobSchedulingService scheduling = schedulingService.getIfAvailable();
        model.addAttribute("schedules", scheduling != null ? scheduling.listSchedules() : List.of());
        model.addAttribute("jobs", jobAdminService.listJobs().stream().filter(j -> j.launchable()).toList());
        return "batch-admin/schedules";
    }

    // ----------------------------------------------------------------------------------------
    // Job actions
    // ----------------------------------------------------------------------------------------

    @PostMapping("/jobs/{jobName}/start")
    public String startJob(@PathVariable String jobName,
                           @RequestParam(name = "parameters", required = false) String parameters,
                           RedirectAttributes redirect) {
        try {
            var execution = jobAdminService.startJob(jobName, new StartJobRequest(parseParameters(parameters)));
            flash(redirect, false, "Started '" + jobName + "' (execution #" + execution.executionId() + ").");
        } catch (BatchAdminException ex) {
            flash(redirect, true, ex.getMessage());
        }
        return redirect(redirect, "/jobs");
    }

    @PostMapping("/jobs")
    public String createJob(@RequestParam String jobName,
                            @RequestParam(required = false) String description,
                            @RequestParam(required = false) String steps,
                            @RequestParam(required = false) String note,
                            RedirectAttributes redirect) {
        try {
            List<StepDefinition> stepDefinitions = parseSteps(steps);
            String name = dynamicJobService.createJob(
                    new CreateJobRequest(jobName, description, stepDefinitions), note);
            flash(redirect, false, "Created job '" + name + "'.");
            return redirect(redirect, "/jobs");
        } catch (BatchAdminException | IllegalArgumentException ex) {
            flash(redirect, true, ex.getMessage());
            return redirect(redirect, "/jobs/new");
        }
    }

    @PostMapping("/jobs/sql-export")
    public String createSqlExport(@RequestParam String jobName,
                                  @RequestParam(required = false) String description,
                                  @RequestParam Map<String, String> form,
                                  RedirectAttributes redirect) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String key : List.of("select", "from", "where", "sort", "pageSize",
                "target", "baseUrl", "index", "idField", "authHeader")) {
            String value = form.get(key);
            if (value != null && !value.isBlank()) {
                properties.put(key, value.trim());
            }
        }
        StepDefinition step = new StepDefinition("export", "sql-export", properties);
        try {
            String name = dynamicJobService.createJob(new CreateJobRequest(jobName, description, List.of(step)));
            flash(redirect, false, "Created export job '" + name + "'.");
            return redirect(redirect, "/jobs");
        } catch (BatchAdminException | IllegalArgumentException ex) {
            flash(redirect, true, ex.getMessage());
            return redirect(redirect, "/jobs/new");
        }
    }

    @PostMapping("/jobs/{jobName}/clone")
    public String cloneJob(@PathVariable String jobName,
                           @RequestParam(required = false) String newName,
                           RedirectAttributes redirect) {
        try {
            String created = dynamicJobService.cloneJob(jobName, newName);
            flash(redirect, false, "Cloned '" + jobName + "' into '" + created + "'.");
        } catch (BatchAdminException | IllegalArgumentException ex) {
            flash(redirect, true, ex.getMessage());
        }
        return redirect(redirect, "/jobs");
    }

    @PostMapping("/jobs/{jobName}/delete")
    public String deleteJob(@PathVariable String jobName, RedirectAttributes redirect) {
        try {
            dynamicJobService.deleteJob(jobName);
            flash(redirect, false, "Deleted job '" + jobName + "'.");
        } catch (BatchAdminException ex) {
            flash(redirect, true, ex.getMessage());
        }
        return redirect(redirect, "/jobs");
    }

    @GetMapping("/jobs/{jobName}/history")
    public String jobHistory(@PathVariable String jobName, Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("active", "jobs");
            model.addAttribute("jobName", jobName);
            model.addAttribute("versions", dynamicJobService.listVersions(jobName));
            return "batch-admin/job-history";
        } catch (BatchAdminException ex) {
            flash(redirect, true, ex.getMessage());
            return redirect(redirect, "/jobs");
        }
    }

    @PostMapping("/jobs/{jobName}/rollback")
    public String rollbackJob(@PathVariable String jobName, @RequestParam int version,
                              RedirectAttributes redirect) {
        try {
            dynamicJobService.rollbackJob(jobName, version);
            flash(redirect, false, "Rolled '" + jobName + "' back to version " + version + ".");
        } catch (BatchAdminException ex) {
            flash(redirect, true, ex.getMessage());
        }
        return redirect(redirect, "/jobs/" + jobName + "/history");
    }

    /** Downloads every dynamic job's definition as a JSON document. */
    @GetMapping("/jobs/export")
    public ResponseEntity<String> exportJobs() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"batch-admin-jobs.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(dynamicJobService.exportAllJson());
    }

    @PostMapping("/jobs/import")
    public String importJobs(@RequestParam(required = false) String json,
                             @RequestParam(defaultValue = "false") boolean overwrite,
                             RedirectAttributes redirect) {
        try {
            ImportResult result = dynamicJobService.importJobsFromJson(json, overwrite);
            StringBuilder message = new StringBuilder("Import: ")
                    .append(result.created().size()).append(" created, ")
                    .append(result.updated().size()).append(" updated, ")
                    .append(result.skipped().size()).append(" skipped");
            if (!result.failed().isEmpty()) {
                message.append(", ").append(result.failed().size()).append(" failed — ")
                        .append(result.failed().entrySet().stream()
                                .map(entry -> entry.getKey() + ": " + entry.getValue())
                                .collect(Collectors.joining("; ")));
            }
            flash(redirect, !result.failed().isEmpty(), message.toString());
        } catch (BatchAdminException ex) {
            flash(redirect, true, ex.getMessage());
        }
        return redirect(redirect, "/jobs");
    }

    // ----------------------------------------------------------------------------------------
    // Execution actions
    // ----------------------------------------------------------------------------------------

    @PostMapping("/executions/{id}/stop")
    public String stop(@PathVariable long id, RedirectAttributes redirect) {
        return executionAction(redirect, () -> jobAdminService.stop(id), "Stop requested for execution #" + id + ".");
    }

    @PostMapping("/executions/{id}/restart")
    public String restart(@PathVariable long id, RedirectAttributes redirect) {
        return executionAction(redirect, () -> jobAdminService.restart(id), "Restarted execution #" + id + ".");
    }

    @PostMapping("/executions/{id}/abandon")
    public String abandon(@PathVariable long id, RedirectAttributes redirect) {
        return executionAction(redirect, () -> jobAdminService.abandon(id), "Abandoned execution #" + id + ".");
    }

    // ----------------------------------------------------------------------------------------
    // Schedule actions
    // ----------------------------------------------------------------------------------------

    @PostMapping("/schedules")
    public String createSchedule(@RequestParam String jobName,
                                 @RequestParam String cron,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(required = false) String parameters,
                                 RedirectAttributes redirect) {
        JobSchedulingService scheduling = schedulingService.getIfAvailable();
        if (scheduling == null) {
            flash(redirect, true, "Scheduling is disabled.");
            return redirect(redirect, "/schedules");
        }
        try {
            scheduling.createSchedule(new ScheduleRequest(jobName, cron, description, true, parseParameters(parameters)));
            flash(redirect, false, "Scheduled '" + jobName + "'.");
        } catch (BatchAdminException ex) {
            flash(redirect, true, ex.getMessage());
        }
        return redirect(redirect, "/schedules");
    }

    @PostMapping("/schedules/{id}/toggle")
    public String toggleSchedule(@PathVariable long id, RedirectAttributes redirect) {
        JobSchedulingService scheduling = schedulingService.getIfAvailable();
        if (scheduling != null) {
            try {
                ScheduleInfo current = scheduling.getSchedule(id);
                scheduling.setEnabled(id, !current.enabled());
            } catch (BatchAdminException ex) {
                flash(redirect, true, ex.getMessage());
            }
        }
        return redirect(redirect, "/schedules");
    }

    @PostMapping("/schedules/{id}/delete")
    public String deleteSchedule(@PathVariable long id, RedirectAttributes redirect) {
        JobSchedulingService scheduling = schedulingService.getIfAvailable();
        if (scheduling != null) {
            try {
                scheduling.deleteSchedule(id);
                flash(redirect, false, "Removed schedule #" + id + ".");
            } catch (BatchAdminException ex) {
                flash(redirect, true, ex.getMessage());
            }
        }
        return redirect(redirect, "/schedules");
    }

    // ----------------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------------

    private String executionAction(RedirectAttributes redirect, Runnable action, String successMessage) {
        try {
            action.run();
            flash(redirect, false, successMessage);
        } catch (BatchAdminException ex) {
            flash(redirect, true, ex.getMessage());
        }
        return redirect(redirect, "/executions");
    }

    private void flash(RedirectAttributes redirect, boolean error, String message) {
        redirect.addFlashAttribute("error", error);
        redirect.addFlashAttribute("message", message);
    }

    private String redirect(RedirectAttributes redirect, String path) {
        return "redirect:" + basePath + path;
    }

    /** Parses a textarea of {@code key=value} lines into a parameter map. */
    static Map<String, String> parseParameters(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return map;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("Parameter line must be 'key=value': " + trimmed);
            }
            map.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        return map;
    }

    /**
     * Parses a textarea describing steps, one per line, in the form:
     * {@code stepName = providerType (key=value, key2=value2)}. The properties block is optional.
     */
    static List<StepDefinition> parseSteps(String text) {
        List<StepDefinition> steps = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return steps;
        }
        for (String line : text.split("\\R")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            Matcher matcher = STEP_LINE.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "Step line must be 'name = type (k=v, ...)': " + line.trim());
            }
            String name = matcher.group(1).trim();
            String type = matcher.group(2).trim();
            Map<String, Object> properties = new LinkedHashMap<>();
            String props = matcher.group(3);
            if (props != null && !props.isBlank()) {
                for (String pair : props.split(",")) {
                    int eq = pair.indexOf('=');
                    if (eq <= 0) {
                        throw new IllegalArgumentException("Property must be 'key=value': " + pair.trim());
                    }
                    properties.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
            steps.add(new StepDefinition(name, type, properties));
        }
        return steps;
    }
}
