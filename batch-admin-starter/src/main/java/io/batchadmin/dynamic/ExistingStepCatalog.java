package io.batchadmin.dynamic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.step.StepLocator;

/**
 * Derives reusable building blocks from the steps of the host application's <b>already-existing</b>
 * jobs. Every step of every registered job that exposes its steps (i.e. implements
 * {@link StepLocator} — the common {@code SimpleJob}/{@code FlowJob} case) becomes a step <i>type</i>
 * operators can drop into a new job composed on the fly, reusing the exact step the host already
 * defined — no code, no duplication.
 *
 * <p>The catalog is recomputed on demand so it always reflects the currently registered jobs. Jobs
 * created <i>through</i> the admin component (dynamic jobs) are excluded, so the component only
 * surfaces the host's genuine building blocks and never feeds its own generated steps back in.</p>
 *
 * <p>The block <b>type</b> is the step's name when that name is unique across the eligible jobs;
 * otherwise it is qualified as {@code <jobName>.<stepName>} so it stays unambiguous.</p>
 */
public class ExistingStepCatalog {

    private final JobRegistry jobRegistry;
    private final Supplier<Set<String>> excludedJobNames;

    /**
     * @param jobRegistry      registry the host's jobs are registered into
     * @param excludedJobNames supplier of job names to skip (typically the dynamic jobs the component
     *                         itself created, so they are not re-derived into blocks)
     */
    public ExistingStepCatalog(JobRegistry jobRegistry, Supplier<Set<String>> excludedJobNames) {
        this.jobRegistry = jobRegistry;
        this.excludedJobNames = excludedJobNames;
    }

    /** A step of an existing job, exposed as a reusable building block. */
    public record ReusableStep(String type, String jobName, Step step) {
    }

    /** Whether {@code type} resolves to a reusable existing step (case-insensitive). */
    public boolean contains(String type) {
        return type != null && catalog().containsKey(type.toLowerCase());
    }

    /** The step for {@code type}, or {@code null} if none (case-insensitive). */
    public Step find(String type) {
        if (type == null) {
            return null;
        }
        ReusableStep reusable = catalog().get(type.toLowerCase());
        return reusable == null ? null : reusable.step();
    }

    /** All reusable steps currently derived from existing jobs, in discovery order. */
    public List<ReusableStep> reusableSteps() {
        return List.copyOf(catalog().values());
    }

    private Map<String, ReusableStep> catalog() {
        Set<String> excluded = excludedJobNames.get();
        List<ReusableStep> found = new ArrayList<>();
        Map<String, Integer> nameCounts = new LinkedHashMap<>();

        for (String jobName : jobRegistry.getJobNames()) {
            if (excluded != null && excluded.contains(jobName)) {
                continue;
            }
            Job job;
            try {
                job = jobRegistry.getJob(jobName);
            } catch (Exception ex) {
                continue;
            }
            if (!(job instanceof StepLocator locator)) {
                continue;
            }
            Collection<String> stepNames;
            try {
                stepNames = locator.getStepNames();
            } catch (Exception ex) {
                continue;
            }
            for (String stepName : stepNames) {
                Step step;
                try {
                    step = locator.getStep(stepName);
                } catch (Exception ex) {
                    continue;
                }
                if (step == null) {
                    continue;
                }
                found.add(new ReusableStep(step.getName(), jobName, step));
                nameCounts.merge(step.getName(), 1, Integer::sum);
            }
        }

        Map<String, ReusableStep> byType = new LinkedHashMap<>();
        for (ReusableStep candidate : found) {
            String stepName = candidate.step().getName();
            String type = nameCounts.getOrDefault(stepName, 0) == 1
                    ? stepName
                    : candidate.jobName() + "." + stepName;
            byType.putIfAbsent(type.toLowerCase(), new ReusableStep(type, candidate.jobName(), candidate.step()));
        }
        return byType;
    }
}
