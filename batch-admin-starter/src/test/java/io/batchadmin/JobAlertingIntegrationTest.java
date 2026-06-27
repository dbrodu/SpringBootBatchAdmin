package io.batchadmin;

import static org.assertj.core.api.Assertions.assertThat;

import io.batchadmin.web.dto.AlertRuleInfo;
import io.batchadmin.web.dto.AlertRuleRequest;
import io.batchadmin.web.dto.ExecutionSummary;
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

/** Covers failure / SLA alerting: rule CRUD, a failure alert and an SLA-overrun alert firing. */
@SpringBootTest(classes = TestBatchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobAlertingIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String api(String path) {
        return "/batch-admin/api" + path;
    }

    private List<Map<String, Object>> recentFor(String jobName) {
        List<Map<String, Object>> all = rest.exchange(api("/alerts/recent"), HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
        return all.stream().filter(a -> jobName.equals(a.get("jobName"))).toList();
    }

    @Test
    void aFailingJobRaisesAFailureAlert() {
        rest.postForEntity(api("/alerts"),
                new AlertRuleRequest("failingTestJob", "FAILURE", null, "LOG", null, true, null),
                AlertRuleInfo.class);

        rest.postForEntity(api("/jobs/failingTestJob/executions"), Map.of("parameters", Map.of()),
                ExecutionSummary.class);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Map<String, Object>> alerts = recentFor("failingTestJob");
            assertThat(alerts).isNotEmpty();
            assertThat(alerts.get(0).get("ruleType")).isEqualTo("FAILURE");
            assertThat((String) alerts.get(0).get("message")).contains("failed");
        });
    }

    @Test
    void aSlowJobRaisesADurationOverrunAlert() {
        // Threshold of 1ms: any real run overruns it.
        rest.postForEntity(api("/alerts"),
                new AlertRuleRequest("sampleTestJob", "DURATION", 1L, "LOG", null, true, null),
                AlertRuleInfo.class);

        rest.postForEntity(api("/jobs/sampleTestJob/executions"), Map.of("parameters", Map.of()),
                ExecutionSummary.class);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Map<String, Object>> alerts = recentFor("sampleTestJob");
            assertThat(alerts).anyMatch(a -> "DURATION".equals(a.get("ruleType")));
        });
    }

    @Test
    void durationRuleWithoutThresholdIsRejected() {
        ResponseEntity<String> response = rest.postForEntity(api("/alerts"),
                new AlertRuleRequest("*", "DURATION", null, "LOG", null, true, null), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void webhookRuleWithoutTargetIsRejected() {
        ResponseEntity<String> response = rest.postForEntity(api("/alerts"),
                new AlertRuleRequest("*", "FAILURE", null, "WEBHOOK", null, true, null), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rulesAreListedToggledDeletedAndTheGuiRenders() {
        AlertRuleInfo rule = rest.postForEntity(api("/alerts"),
                new AlertRuleRequest("*", "FAILURE", null, "LOG", null, true, "catch-all"),
                AlertRuleInfo.class).getBody();
        assertThat(rule).isNotNull();

        ResponseEntity<AlertRuleInfo> toggled = rest.exchange(api("/alerts/" + rule.id() + "/enabled?value=false"),
                HttpMethod.PUT, null, AlertRuleInfo.class);
        assertThat(toggled.getBody().enabled()).isFalse();

        String page = rest.getForObject("/batch-admin/alerts", String.class);
        assertThat(page).contains("Add an alert rule");
        assertThat(page).contains("Recent alerts");

        rest.delete(api("/alerts/" + rule.id()));
        ResponseEntity<String> afterDelete = rest.getForEntity(api("/alerts/" + rule.id()), String.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
