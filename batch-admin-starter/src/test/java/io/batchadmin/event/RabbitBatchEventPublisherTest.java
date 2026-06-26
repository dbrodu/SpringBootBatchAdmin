package io.batchadmin.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitBatchEventPublisherTest {

    private BatchEvent event(BatchEventType type, String jobName) {
        return new BatchEvent(type, jobName, 7L, 3L, "COMPLETED", "COMPLETED", Instant.now(), Map.of());
    }

    @Test
    void sendsToExchangeWithTopicRoutingKey() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitBatchEventPublisher publisher =
                new RabbitBatchEventPublisher(template, "batch.admin.events", "batch.admin");

        BatchEvent event = event(BatchEventType.JOB_COMPLETED, "orders.daily");
        publisher.publish(event);

        // Dots in the job name are escaped so they do not split the topic routing key.
        verify(template).convertAndSend("batch.admin.events", "batch.admin.orders_daily.job_completed",
                (Object) event);
    }

    @Test
    void swallowsBrokerErrorsSoTheJobIsNeverBroken() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doThrow(new AmqpException("broker down"))
                .when(template).convertAndSend(anyString(), anyString(), any(Object.class));
        RabbitBatchEventPublisher publisher = new RabbitBatchEventPublisher(template, "ex", "p");

        assertThatCode(() -> publisher.publish(event(BatchEventType.JOB_FAILED, "j")))
                .doesNotThrowAnyException();
    }
}
