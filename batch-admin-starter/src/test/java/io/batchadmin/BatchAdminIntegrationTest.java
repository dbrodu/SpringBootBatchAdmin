package io.batchadmin;

import static org.assertj.core.api.Assertions.assertThat;

import io.batchadmin.web.dto.CreateJobRequest;
import io.batchadmin.web.dto.ExecutionSummary;
import io.batchadmin.web.dto.JobSummary;
import io.batchadmin.web.dto.ObservabilitySummary;
import io.batchadmin.web.dto.ProviderInfo;
import io.batchadmin.web.dto.ScheduleInfo;
import io.batchadmin.web.dto.ScheduleRequest;
import io.batchadmin.dynamic.StepDefinition;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(classes = TestBatchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BatchAdminIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String api(String path) {
        return "/batch-admin/api" + path;
    }

    @Test
    void discoversHostJobAndDefaultProviders() {
        ResponseEntity<List<JobSummary>> jobs = rest.exchange(api("/jobs"), HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });
        assertThat(jobs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jobs.getBody()).extracting(JobSummary::name).contains("sampleTestJob");

        ResponseEntity<List<ProviderInfo>> providers = rest.exchange(api("/jobs/providers"), HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });
        assertThat(providers.getBody()).extracting(ProviderInfo::type).contains("log", "sleep");
    }

    @Test
    void createsRunsAndObservesADynamicJob() {
        CreateJobRequest request = new CreateJobRequest("itJob", "integration test job",
                List.of(new StepDefinition("hello", "log", Map.of("message", "hi"))));
        ResponseEntity<JobSummary> created = rest.postForEntity(api("/jobs"), request, JobSummary.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().dynamic()).isTrue();

        ResponseEntity<ExecutionSummary> started = rest.postForEntity(api("/jobs/itJob/executions"),
                Map.of("parameters", Map.of()), ExecutionSummary.class);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long executionId = started.getBody().executionId();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ExecutionSummary execution = rest.getForObject(api("/executions/" + executionId), ExecutionSummary.class);
            assertThat(execution.status()).isEqualTo("COMPLETED");
            assertThat(execution.steps()).isNotEmpty();
        });

        ObservabilitySummary summary = rest.getForObject(api("/observability/summary"), ObservabilitySummary.class);
        assertThat(summary.dynamicJobs()).isGreaterThanOrEqualTo(1);
        assertThat(summary.statusCounts().getOrDefault("COMPLETED", 0L)).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void capturesAndReadsExecutionLogsWithLevelFilter() {
        CreateJobRequest request = new CreateJobRequest("logJob", "logs test",
                List.of(new StepDefinition("say", "log", Map.of("message", "hello-from-logs"))));
        rest.postForEntity(api("/jobs"), request, JobSummary.class);

        ResponseEntity<ExecutionSummary> started = rest.postForEntity(api("/jobs/logJob/executions"),
                Map.of("parameters", Map.of()), ExecutionSummary.class);
        long executionId = started.getBody().executionId();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ExecutionSummary execution = rest.getForObject(api("/executions/" + executionId), ExecutionSummary.class);
            assertThat(execution.status()).isEqualTo("COMPLETED");
        });

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            ResponseEntity<List<Map<String, Object>>> logs = rest.exchange(
                    api("/executions/" + executionId + "/logs?level=INFO"), HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {
                    });
            assertThat(logs.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(logs.getBody()).anyMatch(e -> String.valueOf(e.get("message")).contains("hello-from-logs"));
        });

        // Raising the minimum level to ERROR filters the INFO entry out.
        ResponseEntity<List<Map<String, Object>>> errorLogs = rest.exchange(
                api("/executions/" + executionId + "/logs?level=ERROR"), HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });
        assertThat(errorLogs.getBody()).noneMatch(e -> String.valueOf(e.get("message")).contains("hello-from-logs"));
    }

    @Test
    void schedulesAndRemovesACronJob() {
        ScheduleRequest request = new ScheduleRequest("sampleTestJob", "0 0 3 * * *", "nightly", true, Map.of());
        ResponseEntity<ScheduleInfo> created = rest.postForEntity(api("/schedules"), request, ScheduleInfo.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().nextExecution()).isNotNull();

        long id = created.getBody().id();
        rest.delete(api("/schedules/" + id));

        ResponseEntity<List<ScheduleInfo>> list = rest.exchange(api("/schedules"), HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });
        assertThat(list.getBody()).noneMatch(s -> s.id() == id);
    }

    @Test
    void rejectsInvalidCronWithBadRequest() {
        ScheduleRequest request = new ScheduleRequest("sampleTestJob", "not-a-cron", null, true, Map.of());
        ResponseEntity<String> response = rest.postForEntity(api("/schedules"), request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void servesTheThymeleafGui() {
        ResponseEntity<String> dashboard = rest.getForEntity("/batch-admin", String.class);
        assertThat(dashboard.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dashboard.getHeaders().getContentType().toString()).startsWith("text/html");
        assertThat(dashboard.getBody()).contains("Spring Batch Admin");

        ResponseEntity<String> jobs = rest.getForEntity("/batch-admin/jobs", String.class);
        assertThat(jobs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jobs.getBody()).contains("sampleTestJob");
    }
}
