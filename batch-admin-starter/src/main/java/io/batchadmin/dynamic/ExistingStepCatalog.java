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
 * Derives reusable building blocks from the host application's <b>already-existing</b> jobs, in two
 * granularities:
 * <ul>
 *   <li><b>a single step</b> — every step of every registered job becomes a step type
 *       ({@code <stepName>}, or {@code <jobName>.<stepName>} when a name occurs in more than one job);
 *   </li>
 *   <li><b>a whole job's flow</b> — the entire ordered list of a job's steps, referenced as
 *       {@code job:<jobName>}, dropped into a new job as a single block.</li>
 * </ul>
 *
 * <p>Both reuse the host's actual {@link Step} instances. Only jobs that expose their steps (i.e.
 * implement {@link StepLocator} — the common {@code SimpleJob}/{@code FlowJob} case) contribute, and
 * jobs created <i>through</i> the admin component (dynamic jobs) are excluded so the component never
 * feeds its own generated steps back in. The catalog is recomputed on demand so it stays live.</p>
 */
public class ExistingStepCatalog {

    /** Prefix marking a "reuse the whole job's flow" block type. */
    public static final String JOB_TYPE_PREFIX = "job:";

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

    /** A single step of an existing job, exposed as a reusable building block. */
    public record ReusableStep(String type, String jobName, Step step) {
    }

    /** A whole job's ordered steps, exposed as a single reusable building block. */
    public record ReusableJob(String type, String jobName, List<Step> steps) {
    }

    // ---- single steps -----------------------------------------------------------------------

    /** Whether {@code type} resolves to a reusable existing step (case-insensitive). */
    public boolean contains(String type) {
        return type != null && scan().steps.containsKey(type.toLowerCase());
    }

    /** The step for {@code type}, or {@code null} if none (case-insensitive). */
    public Step find(String type) {
        if (type == null) {
            return null;
        }
        ReusableStep reusable = scan().steps.get(type.toLowerCase());
        return reusable == null ? null : reusable.step();
    }

    /** All reusable single steps currently derived from existing jobs, in discovery order. */
    public List<ReusableStep> reusableSteps() {
        return List.copyOf(scan().steps.values());
    }

    // ---- whole jobs -------------------------------------------------------------------------

    /** Whether {@code type} (e.g. {@code job:invoiceJob}) resolves to a reusable whole-job flow. */
    public boolean containsJob(String type) {
        return type != null && scan().jobs.containsKey(type.toLowerCase());
    }

    /** The ordered steps for a {@code job:<name>} type, or {@code null} if none. */
    public List<Step> findJobSteps(String type) {
        if (type == null) {
            return null;
        }
        ReusableJob reusable = scan().jobs.get(type.toLowerCase());
        return reusable == null ? null : reusable.steps();
    }

    /** All reusable whole-job flows currently derived from existing jobs, in discovery order. */
    public List<ReusableJob> reusableJobs() {
        return List.copyOf(scan().jobs.values());
    }

    // ---- internals --------------------------------------------------------------------------

    private record Scan(Map<String, ReusableStep> steps, Map<String, ReusableJob> jobs) {
    }

    private Scan scan() {
        Set<String> excluded = excludedJobNames.get();
        // pass 1: collect each eligible job's ordered steps and count step-name occurrences
        Map<String, List<Step>> stepsByJob = new LinkedHashMap<>();
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
            List<Step> jobSteps = new ArrayList<>();
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
                jobSteps.add(step);
                nameCounts.merge(step.getName(), 1, Integer::sum);
            }
            if (!jobSteps.isEmpty()) {
                stepsByJob.put(jobName, jobSteps);
            }
        }

        // pass 2: build the step blocks (qualifying colliding names) and the whole-job blocks
        Map<String, ReusableStep> steps = new LinkedHashMap<>();
        Map<String, ReusableJob> jobs = new LinkedHashMap<>();
        stepsByJob.forEach((jobName, jobSteps) -> {
            for (Step step : jobSteps) {
                String stepName = step.getName();
                String type = nameCounts.getOrDefault(stepName, 0) == 1 ? stepName : jobName + "." + stepName;
                steps.putIfAbsent(type.toLowerCase(), new ReusableStep(type, jobName, step));
            }
            String jobType = JOB_TYPE_PREFIX + jobName;
            jobs.put(jobType.toLowerCase(), new ReusableJob(jobType, jobName, List.copyOf(jobSteps)));
        });
        return new Scan(steps, jobs);
    }
}
