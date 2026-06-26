package io.batchadmin;

import static org.assertj.core.api.Assertions.assertThat;

import io.batchadmin.dynamic.StepDefinition;
import io.batchadmin.web.dto.CreateJobRequest;
import io.batchadmin.web.dto.ImportResult;
import io.batchadmin.web.dto.JobSummary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Covers exporting dynamic job definitions to JSON and importing them back. */
@SpringBootTest(classes = TestBatchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExportImportJobsIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String api(String path) {
        return "/batch-admin/api" + path;
    }

    @Test
    void exportsDynamicJobsAsJson() {
        rest.postForEntity(api("/jobs"), new CreateJobRequest("expA", "to export",
                List.of(new StepDefinition("s", "log", Map.of("message", "x")))), JobSummary.class);

        ResponseEntity<List<Map<String, Object>>> all = rest.exchange(api("/jobs/export"), HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });
        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(all.getBody()).anyMatch(j -> "expA".equals(j.get("jobName")));

        Map<String, Object> one = rest.getForObject(api("/jobs/expA/export"), Map.class);
        assertThat(one.get("jobName")).isEqualTo("expA");
        assertThat((List<?>) one.get("steps")).hasSize(1);
    }

    @Test
    void importsNewJobsAndSkipsOrOverwritesExistingOnes() {
        // 1) import a brand-new job
        List<CreateJobRequest> doc = List.of(new CreateJobRequest("impB", "imported",
                List.of(new StepDefinition("s", "log", Map.of("message", "one")))));
        ResponseEntity<ImportResult> first = rest.postForEntity(api("/jobs/import"), doc, ImportResult.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().created()).contains("impB");
        assertThat(rest.getForEntity(api("/jobs/impB"), JobSummary.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // 2) re-import the same name without overwrite -> skipped
        ImportResult skip = rest.postForEntity(api("/jobs/import"), doc, ImportResult.class).getBody();
        assertThat(skip.skipped()).contains("impB");
        assertThat(skip.created()).isEmpty();

        // 3) re-import with overwrite=true -> updated
        ImportResult overwrite = rest.postForEntity(api("/jobs/import?overwrite=true"), doc, ImportResult.class).getBody();
        assertThat(overwrite.updated()).contains("impB");
    }

    @Test
    void reportsImportsThatCollideWithADeclaredJobAsFailed() {
        List<CreateJobRequest> doc = List.of(new CreateJobRequest("sampleTestJob", "nope",
                List.of(new StepDefinition("s", "log", Map.of()))));
        ImportResult result = rest.postForEntity(api("/jobs/import"), doc, ImportResult.class).getBody();
        assertThat(result.failed()).containsKey("sampleTestJob");
        assertThat(result.created()).isEmpty();
    }

    @Test
    void guiExposesExportDownloadAndImportPanel() {
        rest.postForEntity(api("/jobs"), new CreateJobRequest("guiExp", null,
                List.of(new StepDefinition("s", "log", Map.of("message", "x")))), JobSummary.class);

        ResponseEntity<String> download = rest.getForEntity("/batch-admin/jobs/export", String.class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getHeaders().getFirst("Content-Disposition")).contains("batch-admin-jobs.json");
        assertThat(download.getBody()).contains("guiExp");

        assertThat(rest.getForObject("/batch-admin/jobs", String.class)).contains("Import / export");
    }
}
