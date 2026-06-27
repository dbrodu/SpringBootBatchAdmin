package io.batchadmin.service;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * Attached to every administered job; on completion it hands the execution to {@link AlertService} so
 * any matching failure/SLA rules fire. Separate from the service to avoid a bean-initialisation cycle.
 */
public class JobAlertListener implements JobExecutionListener {

    private final AlertService alertService;

    public JobAlertListener(AlertService alertService) {
        this.alertService = alertService;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        try {
            alertService.onJobFinished(jobExecution);
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(JobAlertListener.class)
                    .error("[batch-admin] Alert evaluation failed for '{}': {}",
                            jobExecution.getJobInstance().getJobName(), ex.getMessage());
        }
    }
}
