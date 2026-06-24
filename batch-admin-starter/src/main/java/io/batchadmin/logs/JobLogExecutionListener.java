package io.batchadmin.logs;

import org.slf4j.MDC;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * Places the current job execution id into the SLF4J {@link MDC} for the duration of the job, so
 * the {@link io.batchadmin.logs.LogbackJobLogAppender appender} can attribute every log event
 * emitted on the execution's thread to that execution. The id is removed once the job finishes.
 *
 * <p>This listener is attached to every job administered by the component (host {@code Job} beans
 * and jobs created on the fly).</p>
 */
public class JobLogExecutionListener implements JobExecutionListener {

    /** MDC key carrying the job execution id while a job runs. */
    public static final String MDC_EXECUTION_ID = "batchAdminExecutionId";

    /** MDC key carrying the job name while a job runs. */
    public static final String MDC_JOB_NAME = "batchAdminJobName";

    @Override
    public void beforeJob(JobExecution jobExecution) {
        MDC.put(MDC_EXECUTION_ID, String.valueOf(jobExecution.getId()));
        if (jobExecution.getJobInstance() != null) {
            MDC.put(MDC_JOB_NAME, jobExecution.getJobInstance().getJobName());
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        MDC.remove(MDC_EXECUTION_ID);
        MDC.remove(MDC_JOB_NAME);
    }
}
