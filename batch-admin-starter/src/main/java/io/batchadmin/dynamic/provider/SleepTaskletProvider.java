package io.batchadmin.dynamic.provider;

import io.batchadmin.dynamic.TaskletProvider;
import java.util.Map;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Generic provider that sleeps for a configurable duration. Handy to simulate long running steps so
 * that stop/observe features can be demonstrated. Property: {@code millis}.
 *
 * <p>The sleep is genuinely interruptible: it polls the persisted execution status through the
 * {@link JobExplorer} and, when a stop has been requested, flags the step as terminate-only so the
 * framework promptly transitions the execution to {@code STOPPED} instead of waiting for the full
 * duration.</p>
 */
public class SleepTaskletProvider implements TaskletProvider {

    private static final String DEADLINE_KEY = "batchAdmin.sleep.deadline";

    private final JobExplorer jobExplorer;

    public SleepTaskletProvider(JobExplorer jobExplorer) {
        this.jobExplorer = jobExplorer;
    }

    @Override
    public String getType() {
        return "sleep";
    }

    @Override
    public String getDisplayName() {
        return "Sleep";
    }

    @Override
    public Map<String, String> describeProperties() {
        return Map.of("millis", "Sleep duration in milliseconds (default 1000)");
    }

    @Override
    public Tasklet create(Map<String, Object> properties) {
        long millis = toLong(properties.getOrDefault("millis", 1000L), 1000L);
        return (contribution, chunkContext) -> {
            StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
            ExecutionContext context = stepExecution.getExecutionContext();
            long deadline = context.containsKey(DEADLINE_KEY)
                    ? context.getLong(DEADLINE_KEY)
                    : System.currentTimeMillis() + millis;
            context.putLong(DEADLINE_KEY, deadline);

            long jobExecutionId = stepExecution.getJobExecutionId();
            while (System.currentTimeMillis() < deadline) {
                if (stepExecution.isTerminateOnly() || stopRequested(jobExecutionId)) {
                    // Ask the framework to interrupt: it will mark the execution STOPPED.
                    stepExecution.setTerminateOnly();
                    return RepeatStatus.CONTINUABLE;
                }
                Thread.sleep(Math.min(200L, Math.max(1L, deadline - System.currentTimeMillis())));
            }
            return RepeatStatus.FINISHED;
        };
    }

    private boolean stopRequested(long jobExecutionId) {
        JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
        return execution != null && execution.getStatus() == BatchStatus.STOPPING;
    }

    private static long toLong(Object value, long fallback) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
