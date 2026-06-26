package io.batchadmin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The Create job page renders the step-order widget (▲/▼ buttons + drag-and-drop). The reorder itself
 * is client-side; this guards that the markup/script render (i.e. the template parses).
 */
@SpringBootTest(classes = TestBatchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReorderStepsUiTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void createJobPageRendersTheStepOrderControls() {
        ResponseEntity<String> page = rest.getForEntity("/batch-admin/jobs/new", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("id=\"step-order\"");                // the reorder list container
        assertThat(page.getBody()).contains("Use ▲ / ▼ or drag the ⠿ handle");   // the hint (buttons + drag)
        assertThat(page.getBody()).contains("dragstart");                        // the drag-and-drop wiring
        assertThat(page.getBody()).contains("renderOrder");                      // the reorder script
    }
}
