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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Covers version history of a dynamic job's definition and rolling back to a previous version. */
@SpringBootTest(classes = TestBatchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobVersioningIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String api(String path) {
        return "/batch-admin/api" + path;
    }

    private List<Map<String, Object>> versions(String jobName) {
        return rest.exchange(api("/jobs/" + jobName + "/versions"), HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
    }

    private Map<String, Object> version(String jobName, int number) {
        return versions(jobName).stream().filter(v -> v.get("version").equals(number)).findFirst().orElseThrow();
    }

    @Test
    void recordsAVersionOnCreateAndEditThenRollsBack() {
        // v1: one step, created with an explicit change note
        rest.postForEntity(api("/jobs?note=initial-version"), new CreateJobRequest("verA", "v1",
                List.of(new StepDefinition("only", "log", Map.of("message", "one")))), JobSummary.class);
        assertThat(versions("verA")).hasSize(1);
        assertThat(versions("verA").get(0).get("version")).isEqualTo(1);
        assertThat(versions("verA").get(0).get("current")).isEqualTo(true);
        // audit metadata captured on the first version
        assertThat(version("verA", 1).get("changeType")).isEqualTo("CREATE");
        assertThat(version("verA", 1).get("author")).isEqualTo("system");
        assertThat(version("verA", 1).get("changeNote")).isEqualTo("initial-version");

        // v2: two steps (edit)
        rest.exchange(api("/jobs/verA"), HttpMethod.PUT, new HttpEntity<>(new CreateJobRequest("verA", "v2",
                List.of(new StepDefinition("a", "log", Map.of()), new StepDefinition("b", "log", Map.of())))),
                JobSummary.class);
        List<Map<String, Object>> afterEdit = versions("verA");
        assertThat(afterEdit).hasSize(2);
        assertThat(afterEdit).anyMatch(v -> v.get("version").equals(2) && Boolean.TRUE.equals(v.get("current")));
        assertThat(version("verA", 2).get("changeType")).isEqualTo("EDIT");

        // roll back to v1 -> appends v3 with v1's (single-step) content, now current
        ResponseEntity<JobSummary> rolled = rest.postForEntity(api("/jobs/verA/rollback?version=1"), null,
                JobSummary.class);
        assertThat(rolled.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> afterRollback = versions("verA");
        assertThat(afterRollback).hasSize(3);
        assertThat(afterRollback).anyMatch(v -> v.get("version").equals(3) && Boolean.TRUE.equals(v.get("current")));
        // the rollback is itself an audited version
        assertThat(version("verA", 3).get("changeType")).isEqualTo("ROLLBACK");
        assertThat((String) version("verA", 3).get("changeNote")).contains("version 1");

        // the job now runs the rolled-back (single-step) definition
        ResponseEntity<ExecutionSummary> started = rest.postForEntity(api("/jobs/verA/executions"),
                Map.of("parameters", Map.of()), ExecutionSummary.class);
        long executionId = started.getBody().executionId();
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ExecutionSummary execution = rest.getForObject(api("/executions/" + executionId), ExecutionSummary.class);
            assertThat(execution.status()).isEqualTo("COMPLETED");
            assertThat(execution.steps()).hasSize(1);
        });
    }

    @Test
    void rollingBackToAnUnknownVersionIs404() {
        rest.postForEntity(api("/jobs"), new CreateJobRequest("verB", null,
                List.of(new StepDefinition("s", "log", Map.of()))), JobSummary.class);
        ResponseEntity<String> response = rest.postForEntity(api("/jobs/verB/rollback?version=99"), null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void historyPageAndJobsLinkRender() {
        rest.postForEntity(api("/jobs"), new CreateJobRequest("verGui", "desc",
                List.of(new StepDefinition("s", "log", Map.of("message", "x")))), JobSummary.class);

        String page = rest.getForObject("/batch-admin/jobs/verGui/history", String.class);
        assertThat(page).contains("Version history");
        assertThat(page).contains("current");
        // audit columns and metadata render
        assertThat(page).contains("Change");
        assertThat(page).contains("CREATE");
        assertThat(page).contains("system");

        assertThat(rest.getForObject("/batch-admin/jobs", String.class)).contains("/history");
    }
}
