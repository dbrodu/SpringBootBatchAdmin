package io.batchadmin;

import static org.assertj.core.api.Assertions.assertThat;

import io.batchadmin.dynamic.StepDefinition;
import io.batchadmin.web.dto.CloneJobRequest;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Covers previewing a composition (with whole-job expansion) and cloning existing jobs. */
@SpringBootTest(classes = TestBatchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobPreviewAndCloneIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String api(String path) {
        return "/batch-admin/api" + path;
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewExpandsWholeJobBlocksWithoutCreatingTheJob() {
        CreateJobRequest request = new CreateJobRequest("previewJob", "dry run", List.of(
                new StepDefinition("a", "log", Map.of("message", "hi")),
                new StepDefinition("flow", "job:twoStepJob", Map.of())));

        ResponseEntity<Map<String, Object>> preview = rest.exchange(api("/jobs/preview"), HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(request),
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        // log step + the two steps of twoStepJob, expanded in order.
        assertThat(preview.getBody().get("stepCount")).isEqualTo(3);
        List<Map<String, Object>> steps = (List<Map<String, Object>>) preview.getBody().get("steps");
        assertThat(steps).extracting(s -> s.get("stepName"))
                .containsExactly("previewJob.a", "twoStepJob.first", "twoStepJob.second");

        // Preview must not have registered anything.
        ResponseEntity<String> lookup = rest.exchange(api("/jobs/previewJob"), HttpMethod.GET, null, String.class);
        assertThat(lookup.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void clonesADeclaredHostJobIntoARunnableDynamicJob() {
        ResponseEntity<JobSummary> cloned = rest.postForEntity(api("/jobs/sampleTestJob/clone"),
                new CloneJobRequest("sampleClone"), JobSummary.class);
        assertThat(cloned.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(cloned.getBody().name()).isEqualTo("sampleClone");
        assertThat(cloned.getBody().dynamic()).isTrue();

        ResponseEntity<ExecutionSummary> started = rest.postForEntity(api("/jobs/sampleClone/executions"),
                Map.of("parameters", Map.of()), ExecutionSummary.class);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long executionId = started.getBody().executionId();
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(rest.getForObject(api("/executions/" + executionId), ExecutionSummary.class).status())
                        .isEqualTo("COMPLETED"));
    }

    @Test
    void clonesADynamicJobByCopyingItsDefinitions() {
        rest.postForEntity(api("/jobs"), new CreateJobRequest("origDyn", "orig",
                List.of(new StepDefinition("say", "log", Map.of("message", "hello")))), JobSummary.class);

        ResponseEntity<JobSummary> cloned = rest.postForEntity(api("/jobs/origDyn/clone"),
                new CloneJobRequest(null), JobSummary.class);
        assertThat(cloned.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(cloned.getBody().name()).isEqualTo("origDyn-copy");   // default name
        assertThat(cloned.getBody().dynamic()).isTrue();
    }

    @Test
    void jobsAndCreatePagesExposeCloneAndPreviewControls() {
        assertThat(rest.getForObject("/batch-admin/jobs", String.class)).contains("/clone");
        assertThat(rest.getForObject("/batch-admin/jobs/new", String.class)).contains("/jobs/preview");
    }

    @Test
    void guiPreviewRendersTheExpandedSteps() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
        org.springframework.util.MultiValueMap<String, String> form =
                new org.springframework.util.LinkedMultiValueMap<>();
        form.add("jobName", "guiPreview");
        form.add("steps", "a = log (message=hi)\nflow = job:twoStepJob");

        ResponseEntity<String> page = rest.postForEntity("/batch-admin/jobs/preview",
                new org.springframework.http.HttpEntity<>(form, headers), String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        // The preview panel rendered the whole-job block expanded into its individual steps.
        assertThat(page.getBody()).contains("twoStepJob.first").contains("twoStepJob.second");
    }
}
