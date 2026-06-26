package io.batchadmin.dynamic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.batchadmin.dynamic.ExistingStepCatalog.ReusableStep;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.step.StepLocator;

class ExistingStepCatalogTest {

    private Step step(String name) {
        Step step = mock(Step.class);
        lenient().when(step.getName()).thenReturn(name);
        return step;
    }

    /** A job mock that also exposes its steps via {@link StepLocator}. */
    private Job jobWithSteps(JobRegistry registry, String jobName, Step... steps) throws Exception {
        // Resolve names up front so no mock method is invoked inside a when(...) stubbing.
        List<String> names = new java.util.ArrayList<>();
        for (Step s : steps) {
            names.add(s.getName());
        }
        Job job = mock(Job.class, withSettings().extraInterfaces(StepLocator.class));
        StepLocator locator = (StepLocator) job;
        lenient().when(locator.getStepNames()).thenReturn(names);
        for (int i = 0; i < steps.length; i++) {
            lenient().when(locator.getStep(names.get(i))).thenReturn(steps[i]);
        }
        when(registry.getJob(jobName)).thenReturn(job);
        return job;
    }

    @Test
    void derivesOneBlockPerStepOfExistingJobs() throws Exception {
        JobRegistry registry = mock(JobRegistry.class);
        when(registry.getJobNames()).thenReturn(Set.of("invoiceJob"));
        Step extract = step("extract");
        Step load = step("load");
        jobWithSteps(registry, "invoiceJob", extract, load);

        ExistingStepCatalog catalog = new ExistingStepCatalog(registry, Set::of);

        assertThat(catalog.reusableSteps()).extracting(ReusableStep::type).containsExactlyInAnyOrder("extract", "load");
        assertThat(catalog.contains("EXTRACT")).isTrue();   // case-insensitive
        assertThat(catalog.find("load")).isSameAs(load);
        assertThat(catalog.find("missing")).isNull();
    }

    @Test
    void qualifiesStepNamesThatCollideAcrossJobs() throws Exception {
        JobRegistry registry = mock(JobRegistry.class);
        when(registry.getJobNames()).thenReturn(new java.util.LinkedHashSet<>(List.of("jobA", "jobB")));
        jobWithSteps(registry, "jobA", step("load"));
        jobWithSteps(registry, "jobB", step("load"));

        ExistingStepCatalog catalog = new ExistingStepCatalog(registry, Set::of);

        assertThat(catalog.reusableSteps()).extracting(ReusableStep::type)
                .containsExactlyInAnyOrder("jobA.load", "jobB.load");
        assertThat(catalog.contains("load")).isFalse();   // bare name is ambiguous, not offered
    }

    @Test
    void exposesAWholeJobsFlowAsASingleBlock() throws Exception {
        JobRegistry registry = mock(JobRegistry.class);
        when(registry.getJobNames()).thenReturn(Set.of("invoiceJob"));
        Step extract = step("extract");
        Step load = step("load");
        jobWithSteps(registry, "invoiceJob", extract, load);

        ExistingStepCatalog catalog = new ExistingStepCatalog(registry, Set::of);

        assertThat(catalog.reusableJobs()).extracting(ExistingStepCatalog.ReusableJob::type)
                .containsExactly("job:invoiceJob");
        assertThat(catalog.containsJob("JOB:invoiceJob")).isTrue();        // case-insensitive
        assertThat(catalog.findJobSteps("job:invoiceJob")).containsExactly(extract, load);  // in order
        assertThat(catalog.findJobSteps("job:missing")).isNull();
    }

    @Test
    void excludesTheGivenJobs() throws Exception {
        JobRegistry registry = mock(JobRegistry.class);
        when(registry.getJobNames()).thenReturn(new java.util.LinkedHashSet<>(List.of("hostJob", "dynamicJob")));
        jobWithSteps(registry, "hostJob", step("real"));
        jobWithSteps(registry, "dynamicJob", step("generated"));

        ExistingStepCatalog catalog = new ExistingStepCatalog(registry, () -> Set.of("dynamicJob"));

        assertThat(catalog.reusableSteps()).extracting(ReusableStep::type).containsExactly("real");
    }
}
