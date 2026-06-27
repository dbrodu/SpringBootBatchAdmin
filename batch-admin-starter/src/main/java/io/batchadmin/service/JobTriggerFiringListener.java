package io.batchadmin.service;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * Attached to every administered job; on completion it hands the execution to
 * {@link JobTriggerService} so any matching chaining rules fire. Kept separate from the service to
 * avoid a bean-initialisation cycle (the service depends on {@code JobAdminService}, which is part
 * of the listener set attached to jobs).
 */
public class JobTriggerFiringListener implements JobExecutionListener {

    private final JobTriggerService triggerService;

    public JobTriggerFiringListener(JobTriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        try {
            triggerService.onJobFinished(jobExecution);
        } catch (RuntimeException ex) {
            // A misbehaving trigger must never fail the job that was observed.
            org.slf4j.LoggerFactory.getLogger(JobTriggerFiringListener.class)
                    .error("[batch-admin] Trigger evaluation failed for '{}': {}",
                            jobExecution.getJobInstance().getJobName(), ex.getMessage());
        }
    }
}
