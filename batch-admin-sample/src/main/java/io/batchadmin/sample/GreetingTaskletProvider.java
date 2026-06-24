package io.batchadmin.sample;

import io.batchadmin.dynamic.TaskletProvider;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Example of a host-provided building block. Declaring it as a Spring bean is enough for it to
 * appear in the admin GUI as a step type operators can use when composing jobs on the fly.
 */
@Component
public class GreetingTaskletProvider implements TaskletProvider {

    private static final Logger log = LoggerFactory.getLogger(GreetingTaskletProvider.class);

    @Override
    public String getType() {
        return "greeting";
    }

    @Override
    public String getDisplayName() {
        return "Greet someone";
    }

    @Override
    public Map<String, String> describeProperties() {
        return Map.of("name", "Who to greet (default: world)");
    }

    @Override
    public Tasklet create(Map<String, Object> properties) {
        String name = String.valueOf(properties.getOrDefault("name", "world"));
        return (contribution, chunkContext) -> {
            log.info("[sample] Hello, {}!", name);
            return RepeatStatus.FINISHED;
        };
    }
}
