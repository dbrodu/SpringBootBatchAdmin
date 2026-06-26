package io.batchadmin;

import static org.assertj.core.api.Assertions.assertThat;

import io.batchadmin.dynamic.StepDefinition;
import io.batchadmin.web.dto.CreateJobRequest;
import io.batchadmin.web.dto.ExecutionSummary;
import io.batchadmin.web.dto.JobSummary;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Verifies that the steps of an existing host job are derived into reusable building blocks and can be
 * dropped into a new on-the-fly job, which then runs to completion reusing the original step.
 */
@SpringBootTest(classes = TestBatchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReusableStepsIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String api(String path) {
        return "/batch-admin/api" + path;
    }

    @Test
    void exposesExistingJobStepsAsReusableBlocksAndComposesWithThem() {
        // The host job 'sampleTestJob' contributes its step 'sampleTestJob.step' as a reusable block.
        ResponseEntity<List<Map<String, Object>>> blocks = rest.exchange(api("/jobs/reusable-steps"),
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                });
        assertThat(blocks.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(blocks.getBody()).anyMatch(b -> "sampleTestJob.step".equals(b.get("type")));

        // Compose a brand-new job that reuses that existing step as its only step.
        CreateJobRequest request = new CreateJobRequest("reuseExistingJob", "reuses a host step",
                List.of(new StepDefinition("run", "sampleTestJob.step", Map.of())));
        ResponseEntity<JobSummary> created = rest.postForEntity(api("/jobs"), request, JobSummary.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().dynamic()).isTrue();

        // It launches and completes, running the reused step.
        ResponseEntity<ExecutionSummary> started = rest.postForEntity(api("/jobs/reuseExistingJob/executions"),
                Map.of("parameters", Map.of()), ExecutionSummary.class);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long executionId = started.getBody().executionId();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ExecutionSummary execution = rest.getForObject(api("/executions/" + executionId), ExecutionSummary.class);
            assertThat(execution.status()).isEqualTo("COMPLETED");
            assertThat(execution.steps()).isNotEmpty();
        });
    }
}
