package io.batchadmin.web;

import io.batchadmin.service.DynamicJobService;
import io.batchadmin.service.JobAdminService;
import io.batchadmin.web.dto.CloneJobRequest;
import io.batchadmin.web.dto.CreateJobRequest;
import io.batchadmin.web.dto.ExecutionSummary;
import io.batchadmin.web.dto.ImportResult;
import io.batchadmin.web.dto.JobPreview;
import io.batchadmin.web.dto.JobSummary;
import io.batchadmin.web.dto.JobVersionInfo;
import io.batchadmin.web.dto.ProviderInfo;
import io.batchadmin.web.dto.StartJobRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * REST endpoints to discover, launch, create on the fly and delete jobs.
 */
@RestController
@RequestMapping("${batch.admin.base-path:/batch-admin}/api/jobs")
public class JobController {

    private final JobAdminService jobAdminService;
    private final DynamicJobService dynamicJobService;

    public JobController(JobAdminService jobAdminService, DynamicJobService dynamicJobService) {
        this.jobAdminService = jobAdminService;
        this.dynamicJobService = dynamicJobService;
    }

    @GetMapping
    public List<JobSummary> listJobs() {
        return jobAdminService.listJobs();
    }

    /** Building blocks operators can compose into a job. Declared before {@code /{jobName}}. */
    @GetMapping("/providers")
    public List<ProviderInfo> listProviders() {
        return dynamicJobService.listProviders();
    }

    /** Building blocks derived from the steps of the host's existing jobs (reusable as step types). */
    @GetMapping("/reusable-steps")
    public List<ProviderInfo> listReusableSteps() {
        return dynamicJobService.listReusableSteps();
    }

    /** Building blocks that reuse a whole existing job's flow (all its steps), typed {@code job:<name>}. */
    @GetMapping("/reusable-jobs")
    public List<ProviderInfo> listReusableJobs() {
        return dynamicJobService.listReusableJobs();
    }

    /** Portable JSON of every dynamic job's definition (to move jobs between environments). */
    @GetMapping("/export")
    public List<CreateJobRequest> exportJobs() {
        return dynamicJobService.exportAll();
    }

    /** Creates/overwrites dynamic jobs from a previously exported JSON array. */
    @PostMapping("/import")
    public ImportResult importJobs(@RequestBody List<CreateJobRequest> definitions,
                                   @RequestParam(defaultValue = "false") boolean overwrite) {
        return dynamicJobService.importJobs(definitions, overwrite);
    }

    @GetMapping("/{jobName}")
    public JobSummary getJob(@PathVariable String jobName) {
        return jobAdminService.getJob(jobName);
    }

    /** Portable JSON of one dynamic job's definition. */
    @GetMapping("/{jobName}/export")
    public CreateJobRequest exportJob(@PathVariable String jobName) {
        return dynamicJobService.exportJob(jobName);
    }

    /** A dynamic job's version history (newest first). */
    @GetMapping("/{jobName}/versions")
    public List<JobVersionInfo> listVersions(@PathVariable String jobName) {
        return dynamicJobService.listVersions(jobName);
    }

    /** Rolls a dynamic job back to a previous version (recorded as a new version). */
    @PostMapping("/{jobName}/rollback")
    public JobSummary rollback(@PathVariable String jobName, @RequestParam int version) {
        return jobAdminService.getJob(dynamicJobService.rollbackJob(jobName, version));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobSummary createJob(@RequestBody CreateJobRequest request,
                                @RequestParam(required = false) String note) {
        String name = dynamicJobService.createJob(request, note);
        return jobAdminService.getJob(name);
    }

    /** Dry-run a composition: returns the expanded step list without creating anything. */
    @PostMapping("/preview")
    public JobPreview previewJob(@RequestBody CreateJobRequest request) {
        return dynamicJobService.previewJob(request);
    }

    /** Clones an existing job (declared or dynamic) into a new dynamic job. */
    @PostMapping("/{jobName}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public JobSummary cloneJob(@PathVariable String jobName,
                               @RequestBody(required = false) CloneJobRequest request) {
        String created = dynamicJobService.cloneJob(jobName, request == null ? null : request.newName());
        return jobAdminService.getJob(created);
    }

    /** Replaces an existing dynamic job's steps/description in place (the name is fixed). */
    @PutMapping("/{jobName}")
    public JobSummary updateJob(@PathVariable String jobName, @RequestBody CreateJobRequest request,
                                @RequestParam(required = false) String note) {
        String name = dynamicJobService.updateJob(jobName, request, note);
        return jobAdminService.getJob(name);
    }

    @DeleteMapping("/{jobName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJob(@PathVariable String jobName) {
        dynamicJobService.deleteJob(jobName);
    }

    @PostMapping("/{jobName}/executions")
    public ResponseEntity<ExecutionSummary> startJob(@PathVariable String jobName,
                                                     @RequestBody(required = false) StartJobRequest request) {
        ExecutionSummary execution = jobAdminService.startJob(
                jobName, request != null ? request : new StartJobRequest(null));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(execution);
    }

    @GetMapping("/{jobName}/executions")
    public List<ExecutionSummary> listExecutions(@PathVariable String jobName,
                                                 @RequestParam(defaultValue = "20") int limit) {
        return jobAdminService.listExecutions(jobName, limit);
    }
}
