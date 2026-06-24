package io.batchadmin.dynamic;

import java.util.Map;
import org.springframework.batch.core.step.tasklet.Tasklet;

/**
 * Extension point that keeps the admin component agnostic of the host application's jobs.
 *
 * <p>Host applications contribute {@code TaskletProvider} beans describing the elementary
 * operations their domain supports (e.g. "import-file", "send-report", "purge-table"). The admin
 * GUI then lets operators compose jobs on the fly from the available provider types, and the
 * {@link DynamicJobService} wires the resulting {@link Tasklet}s into real Spring Batch steps.</p>
 *
 * <p>Providers are discovered automatically as Spring beans. The component ships a couple of
 * generic providers (logging, sleep, optional system command) so it is usable out of the box.</p>
 */
public interface TaskletProvider {

    /**
     * Logical type of step this provider can build, referenced from a
     * {@link StepDefinition#type()}. Must be unique across all providers.
     */
    String getType();

    /** Human readable label shown in the GUI. */
    default String getDisplayName() {
        return getType();
    }

    /** Optional documentation of the properties this provider understands, shown in the GUI. */
    default Map<String, String> describeProperties() {
        return Map.of();
    }

    /**
     * Builds a tasklet for a step, using the supplied step properties.
     *
     * @param properties the {@link StepDefinition#properties()} of the step being built
     * @return a ready-to-run tasklet; never {@code null}
     */
    Tasklet create(Map<String, Object> properties);
}
