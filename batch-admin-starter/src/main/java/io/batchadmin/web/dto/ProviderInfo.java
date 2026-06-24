package io.batchadmin.web.dto;

import java.util.Map;

/**
 * View of an available {@link io.batchadmin.dynamic.TaskletProvider}, used by the GUI to offer
 * the building blocks operators can assemble into a job.
 */
public record ProviderInfo(String type, String displayName, Map<String, String> properties) {
}
