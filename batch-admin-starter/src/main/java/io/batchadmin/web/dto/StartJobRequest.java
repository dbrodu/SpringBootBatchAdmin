package io.batchadmin.web.dto;

import java.util.Map;

/**
 * Request body to start a job. Parameters are passed as strings and converted to typed
 * {@code JobParameter}s; an automatic {@code run.id} is added so a job can be relaunched.
 */
public record StartJobRequest(Map<String, String> parameters) {

    public StartJobRequest {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
