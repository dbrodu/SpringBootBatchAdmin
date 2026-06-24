package io.batchadmin.dynamic.provider;

import io.batchadmin.dynamic.TaskletProvider;
import java.util.Map;
import org.springframework.batch.core.step.tasklet.SystemCommandTasklet;
import org.springframework.batch.core.step.tasklet.Tasklet;

/**
 * Provider that runs an operating-system command via {@link SystemCommandTasklet}.
 *
 * <p>This is powerful and therefore opt-in: it is only registered when
 * {@code batch.admin.dynamic-jobs.allow-command-tasklets=true}. Property: {@code command},
 * optional {@code timeoutMillis}, {@code workingDirectory}.</p>
 */
public class CommandTaskletProvider implements TaskletProvider {

    @Override
    public String getType() {
        return "command";
    }

    @Override
    public String getDisplayName() {
        return "Run system command";
    }

    @Override
    public Map<String, String> describeProperties() {
        return Map.of(
                "command", "Command line to execute",
                "timeoutMillis", "Timeout in milliseconds (default 60000)",
                "workingDirectory", "Optional working directory");
    }

    @Override
    public Tasklet create(Map<String, Object> properties) {
        String command = String.valueOf(properties.getOrDefault("command", ""));
        if (command.isBlank()) {
            throw new IllegalArgumentException("'command' property is required for a command step");
        }
        long timeout = toLong(properties.getOrDefault("timeoutMillis", 60_000L), 60_000L);

        SystemCommandTasklet tasklet = new SystemCommandTasklet();
        tasklet.setCommand(command.trim().split("\\s+"));
        tasklet.setTimeout(timeout);
        Object workingDir = properties.get("workingDirectory");
        if (workingDir != null && !String.valueOf(workingDir).isBlank()) {
            tasklet.setWorkingDirectory(String.valueOf(workingDir));
        }
        try {
            tasklet.afterPropertiesSet();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to configure command tasklet", ex);
        }
        return tasklet;
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
