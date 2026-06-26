package io.batchadmin.event;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * Bridges Spring Batch job lifecycle callbacks to {@link BatchEvent}s and hands them to the
 * configured {@link BatchEventPublisher}. Attached to every job administered by the component (host
 * {@code Job} beans and jobs created on the fly), exactly like the per-execution log listener.
 */
public class BatchEventPublishingListener implements JobExecutionListener {

    private final BatchEventPublisher publisher;

    /**
     * @param publisher the configured publisher, or {@code null} when events are enabled but no
     *                  publisher could be resolved (a misconfiguration) — in which case this listener
     *                  is an inert no-op rather than failing the jobs it observes.
     */
    public BatchEventPublishingListener(BatchEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        if (publisher != null) {
            publisher.publish(BatchEvent.from(BatchEventType.JOB_STARTED, jobExecution));
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (publisher != null) {
            publisher.publish(BatchEvent.from(terminalType(jobExecution.getStatus()), jobExecution));
        }
    }

    private static BatchEventType terminalType(BatchStatus status) {
        if (status == BatchStatus.COMPLETED) {
            return BatchEventType.JOB_COMPLETED;
        }
        if (status == BatchStatus.STOPPED) {
            return BatchEventType.JOB_STOPPED;
        }
        return BatchEventType.JOB_FAILED;
    }
}
