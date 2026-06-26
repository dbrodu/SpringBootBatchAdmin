package io.batchadmin.dynamic;

import java.util.Map;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Extension point for contributing whole {@link Step}s (typically chunk-oriented: reader / processor
 * / writer) to dynamically created jobs — a richer counterpart to {@link TaskletProvider}, which
 * only contributes a single {@code Tasklet}.
 *
 * <p>The component ships a {@code sql-export} provider (paged SQL reader &rarr; JSON &rarr; a target
 * such as OpenSearch). Host applications can contribute their own step providers as Spring beans;
 * they then become usable as step types when composing jobs through the API and GUI.</p>
 */
public interface StepProvider {

    /** Logical step type, referenced from a {@link StepDefinition#type()}. Must be unique. */
    String getType();

    /** Human-readable label shown in the GUI. */
    default String getDisplayName() {
        return getType();
    }

    /** Optional documentation of the properties this provider understands, shown in the GUI. */
    default Map<String, String> describeProperties() {
        return Map.of();
    }

    /**
     * Builds a fully configured step.
     *
     * @param stepName   the unique step name to use
     * @param properties the {@link StepDefinition#properties()} of the step being built
     * @param context    batch infrastructure needed to build the step
     */
    Step buildStep(String stepName, Map<String, Object> properties, Context context);

    /** Batch infrastructure handed to a {@link StepProvider} when it builds a step. */
    record Context(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
    }
}
