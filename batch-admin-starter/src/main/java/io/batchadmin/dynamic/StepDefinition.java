package io.batchadmin.dynamic;

import java.util.Map;

/**
 * Declarative definition of one step of a dynamically created job.
 *
 * @param name       step name, unique within its job
 * @param type       logical type resolved against a {@link TaskletProvider#getType()}
 * @param properties free-form configuration handed to the {@link TaskletProvider}
 */
public record StepDefinition(String name, String type, Map<String, Object> properties) {

    public StepDefinition {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
