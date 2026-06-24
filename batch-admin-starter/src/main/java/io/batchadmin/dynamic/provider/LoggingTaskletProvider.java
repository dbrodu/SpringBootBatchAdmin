package io.batchadmin.dynamic.provider;

import io.batchadmin.dynamic.TaskletProvider;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Generic provider that logs a message. Useful as a no-op marker step and as a smoke test
 * for the dynamic job pipeline. Property: {@code message}.
 */
public class LoggingTaskletProvider implements TaskletProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingTaskletProvider.class);

    @Override
    public String getType() {
        return "log";
    }

    @Override
    public String getDisplayName() {
        return "Log a message";
    }

    @Override
    public Map<String, String> describeProperties() {
        return Map.of("message", "Text to write to the application log");
    }

    @Override
    public Tasklet create(Map<String, Object> properties) {
        String message = String.valueOf(properties.getOrDefault("message", "batch-admin: step executed"));
        return (StepContribution contribution, ChunkContext chunkContext) -> {
            log.info("[batch-admin] {}", message);
            return RepeatStatus.FINISHED;
        };
    }
}
