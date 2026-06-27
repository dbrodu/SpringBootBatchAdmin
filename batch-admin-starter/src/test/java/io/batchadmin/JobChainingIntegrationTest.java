package io.batchadmin;

import static org.assertj.core.api.Assertions.assertThat;

import io.batchadmin.dynamic.StepDefinition;
import io.batchadmin.web.dto.CreateJobRequest;
import io.batchadmin.web.dto.ExecutionSummary;
import io.batchadmin.web.dto.JobSummary;
import io.batchadmin.web.dto.JobTriggerInfo;
import io.batchadmin.web.dto.JobTriggerRequest;
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

/** Covers event-driven job chaining: trigger CRUD, validation and a source→target launch. */
@SpringBootTest(classes = TestBatchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobChainingIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String api(String path) {
        return "/batch-admin/api" + path;
    }

    private void createJob(String name) {
        rest.postForEntity(api("/jobs"), new CreateJobRequest(name, null,
                List.of(new StepDefinition("s", "log", Map.of("message", "hi")))), JobSummary.class);
    }

    private List<Map<String, Object>> executions(String jobName) {
        return rest.exchange(api("/jobs/" + jobName + "/executions"), HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
    }

    @Test
    void triggerLaunchesTheTargetWhenTheSourceSucceeds() {
        createJob("chainA");
        createJob("chainB");
        ResponseEntity<JobTriggerInfo> created = rest.postForEntity(api("/triggers"),
                new JobTriggerRequest("chainA", "chainB", "SUCCESS", true, false, null, "demo"),
                JobTriggerInfo.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().condition()).isEqualTo("SUCCESS");

        // Launch the source; the target must run on its own.
        rest.postForEntity(api("/jobs/chainA/executions"), Map.of("parameters", Map.of()), ExecutionSummary.class);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Map<String, Object>> bExecutions = executions("chainB");
            assertThat(bExecutions).isNotEmpty();
            assertThat(bExecutions.get(0).get("status")).isEqualTo("COMPLETED");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void triggerForwardsSourceParametersAndAddsStaticOnes() {
        createJob("paramA");
        createJob("paramB");
        // Inherit the source's params and add a static one.
        rest.postForEntity(api("/triggers"),
                new JobTriggerRequest("paramA", "paramB", "SUCCESS", true, true,
                        Map.of("mode", "full"), null), JobTriggerInfo.class);

        // Launch the source with a parameter that should flow downstream.
        rest.postForEntity(api("/jobs/paramA/executions"),
                Map.of("parameters", Map.of("region", "EU")), ExecutionSummary.class);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Map<String, Object>> bExecutions = executions("paramB");
            assertThat(bExecutions).isNotEmpty();
            Map<String, Object> latest = bExecutions.get(0);
            assertThat(latest.get("status")).isEqualTo("COMPLETED");
            Map<String, Object> params = (Map<String, Object>) latest.get("parameters");
            assertThat(params).containsEntry("region", "EU");          // forwarded from the source
            assertThat(params).containsEntry("mode", "full");           // static, defined on the trigger
            assertThat(params).containsEntry("batchAdmin.chainSource", "paramA");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void theTriggerGraphExposesNodesEdgesAndRendersAsSvg() {
        createJob("g1");
        createJob("g2");
        createJob("g3");
        rest.postForEntity(api("/triggers"),
                new JobTriggerRequest("g1", "g2", "SUCCESS", true, false, null, null), JobTriggerInfo.class);
        rest.postForEntity(api("/triggers"),
                new JobTriggerRequest("g2", "g3", "SUCCESS", true, false, null, null), JobTriggerInfo.class);

        Map<String, Object> graph = rest.getForObject(api("/triggers/graph"), Map.class);
        assertThat((List<Object>) graph.get("nodes")).hasSize(3);
        assertThat((List<Object>) graph.get("edges")).hasSize(2);

        String page = rest.getForObject("/batch-admin/pipelines/graph", String.class);
        assertThat(page).contains("Pipeline graph");
        assertThat(page).contains("<svg");
        assertThat(page).contains("g1");
        assertThat(page).contains("g3");
    }

    @Test
    void aJobCannotTriggerItself() {
        createJob("selfJob");
        ResponseEntity<String> response = rest.postForEntity(api("/triggers"),
                new JobTriggerRequest("selfJob", "selfJob", "ANY", true, false, null, null), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aTriggerToAnUnknownJobIsRejected() {
        createJob("realJob");
        ResponseEntity<String> response = rest.postForEntity(api("/triggers"),
                new JobTriggerRequest("realJob", "ghostJob", "SUCCESS", true, false, null, null), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void triggersCanBeListedToggledDeletedAndRenderedInTheGui() {
        createJob("srcJob");
        createJob("dstJob");
        JobTriggerInfo trigger = rest.postForEntity(api("/triggers"),
                new JobTriggerRequest("srcJob", "dstJob", "ANY", true, false, null, null),
                JobTriggerInfo.class).getBody();
        assertThat(trigger).isNotNull();

        List<Map<String, Object>> all = rest.exchange(api("/triggers"), HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
        assertThat(all).anyMatch(t -> t.get("sourceJob").equals("srcJob") && t.get("targetJob").equals("dstJob"));

        ResponseEntity<JobTriggerInfo> toggled = rest.exchange(
                api("/triggers/" + trigger.id() + "/enabled?value=false"), HttpMethod.PUT, null,
                JobTriggerInfo.class);
        assertThat(toggled.getBody().enabled()).isFalse();

        // The Pipelines GUI screen renders.
        String page = rest.getForObject("/batch-admin/pipelines", String.class);
        assertThat(page).contains("Chain a job to another");
        assertThat(page).contains("srcJob");

        rest.delete(api("/triggers/" + trigger.id()));
        ResponseEntity<String> afterDelete = rest.getForEntity(api("/triggers/" + trigger.id()), String.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
