package io.batchadmin.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A couple of pre-existing jobs, declared as ordinary Spring beans. The admin component discovers
 * and registers them automatically, demonstrating that it is agnostic of the host application's jobs.
 */
@Configuration
public class SampleJobsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SampleJobsConfiguration.class);

    @Bean
    public Job dailyReportJob(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        Step extract = new StepBuilder("dailyReport.extract", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[sample] Extracting data for the daily report");
                    Thread.sleep(500);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();

        Step render = new StepBuilder("dailyReport.render", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[sample] Rendering the daily report");
                    Thread.sleep(500);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();

        return new JobBuilder("dailyReportJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(extract)
                .next(render)
                .build();
    }

    @Bean
    public Job housekeepingJob(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        Step purge = new StepBuilder("housekeeping.purge", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[sample] Purging stale temporary files");
                    Thread.sleep(800);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();

        return new JobBuilder("housekeepingJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(purge)
                .build();
    }
}
