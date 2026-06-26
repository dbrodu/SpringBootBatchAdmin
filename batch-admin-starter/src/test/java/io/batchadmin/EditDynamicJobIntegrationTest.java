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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Covers editing an existing dynamic job in place (REST + GUI). */
@SpringBootTest(classes = TestBatchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EditDynamicJobIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String api(String path) {
        return "/batch-admin/api" + path;
    }

    @Test
    void editsADynamicJobsStepsInPlace() {
        rest.postForEntity(api("/jobs"), new CreateJobRequest("editable", "v1",
                List.of(new StepDefinition("only", "log", Map.of("message", "one")))), JobSummary.class);

        // Replace the single step with two steps.
        CreateJobRequest update = new CreateJobRequest("editable", "v2", List.of(
                new StepDefinition("first", "log", Map.of("message", "a")),
                new StepDefinition("second", "log", Map.of("message", "b"))));
        ResponseEntity<JobSummary> updated = rest.exchange(api("/jobs/editable"), HttpMethod.PUT,
                new HttpEntity<>(update), JobSummary.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().dynamic()).isTrue();

        // Running the edited job now executes both steps.
        ResponseEntity<ExecutionSummary> started = rest.postForEntity(api("/jobs/editable/executions"),
                Map.of("parameters", Map.of()), ExecutionSummary.class);
        long executionId = started.getBody().executionId();
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ExecutionSummary execution = rest.getForObject(api("/executions/" + executionId), ExecutionSummary.class);
            assertThat(execution.status()).isEqualTo("COMPLETED");
            assertThat(execution.steps()).hasSize(2);
        });
    }

    @Test
    void refusesToEditADeclaredHostJob() {
        CreateJobRequest update = new CreateJobRequest("sampleTestJob", "nope",
                List.of(new StepDefinition("x", "log", Map.of())));
        ResponseEntity<String> response = rest.exchange(api("/jobs/sampleTestJob"), HttpMethod.PUT,
                new HttpEntity<>(update), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void editPagePreFillsTheComposerAndJobsScreenLinksToIt() {
        rest.postForEntity(api("/jobs"), new CreateJobRequest("editGui", "desc",
                List.of(new StepDefinition("say", "log", Map.of("message", "hi")))), JobSummary.class);

        String editPage = rest.getForObject("/batch-admin/jobs/editGui/edit", String.class);
        assertThat(editPage).contains("Edit job");                 // edit-mode heading
        assertThat(editPage).contains("say = log (message=hi)");   // steps reconstructed into the textarea
        assertThat(editPage).contains("Save changes");

        assertThat(rest.getForObject("/batch-admin/jobs", String.class)).contains("/editGui/edit");
    }
}
