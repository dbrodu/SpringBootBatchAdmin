package io.batchadmin.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.batchadmin.TestBatchApplication;
import io.batchadmin.web.dto.ExecutionSummary;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Verifies the default (in-process) event pipeline: launching a job publishes {@code JOB_STARTED}
 * then {@code JOB_COMPLETED} {@link BatchEvent}s, consumable by any host bean via
 * {@code @EventListener}.
 */
@SpringBootTest(classes = TestBatchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(BatchEventsIntegrationTest.EventCaptor.class)
class BatchEventsIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private EventCaptor captor;

    @Test
    void publishesLifecycleEventsForAJobRun() {
        rest.postForEntity("/batch-admin/api/jobs/sampleTestJob/executions",
                Map.of("parameters", Map.of()), ExecutionSummary.class);

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<BatchEvent> events = captor.events();
            assertThat(events).extracting(BatchEvent::type)
                    .contains(BatchEventType.JOB_STARTED, BatchEventType.JOB_COMPLETED);
            assertThat(events).filteredOn(e -> e.type() == BatchEventType.JOB_COMPLETED)
                    .allSatisfy(e -> {
                        assertThat(e.jobName()).isEqualTo("sampleTestJob");
                        assertThat(e.status()).isEqualTo("COMPLETED");
                        assertThat(e.executionId()).isNotNull();
                    });
        });
    }

    @Component
    static class EventCaptor {
        private final List<BatchEvent> events = Collections.synchronizedList(new ArrayList<>());

        @EventListener
        void on(BatchEvent event) {
            events.add(event);
        }

        List<BatchEvent> events() {
            return new ArrayList<>(events);
        }
    }
}
