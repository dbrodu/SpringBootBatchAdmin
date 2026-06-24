package io.batchadmin.web.dto;

import io.batchadmin.dynamic.StepDefinition;
import java.util.List;

/**
 * Request body to create a job on the fly from an ordered list of step definitions.
 */
public record CreateJobRequest(String jobName, String description, List<StepDefinition> steps) {

    public CreateJobRequest {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
