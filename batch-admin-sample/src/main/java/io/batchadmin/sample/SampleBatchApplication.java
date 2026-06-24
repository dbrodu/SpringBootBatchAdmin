package io.batchadmin.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo Spring Boot Batch application. It declares a couple of jobs and a custom tasklet provider;
 * the admin component initializes itself and exposes the GUI at {@code /batch-admin} plus the REST
 * API under {@code /batch-admin/api} on the application port, with no further wiring required.
 */
@SpringBootApplication
public class SampleBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleBatchApplication.class, args);
    }
}
