package io.batchadmin;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

/** Minimal host application used to exercise the auto-configuration in tests. */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestBatchApplication {

    @Bean
    public Job sampleTestJob(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new JobBuilder("sampleTestJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(new StepBuilder("sampleTestJob.step", jobRepository)
                        .tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED, transactionManager)
                        .build())
                .build();
    }

    /** A host job whose single step always fails — used to exercise failure alerting. */
    @Bean
    public Job failingTestJob(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new JobBuilder("failingTestJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(new StepBuilder("failingTestJob.step", jobRepository)
                        .tasklet((contribution, chunkContext) -> {
                            throw new IllegalStateException("boom");
                        }, transactionManager)
                        .build())
                .build();
    }

    /** A two-step host job, used to exercise reusing a whole job's flow as one building block. */
    @Bean
    public Job twoStepJob(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new JobBuilder("twoStepJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(new StepBuilder("twoStepJob.first", jobRepository)
                        .tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED, transactionManager)
                        .build())
                .next(new StepBuilder("twoStepJob.second", jobRepository)
                        .tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED, transactionManager)
                        .build())
                .build();
    }
}
