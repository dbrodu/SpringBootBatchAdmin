package io.batchadmin.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Opt-in {@link BatchEventPublisher} that forwards events to a RabbitMQ broker. Each event is sent to
 * a configurable topic exchange with the routing key
 * {@code <prefix>.<jobName>.<type>} (e.g. {@code batch.admin.ordersJob.job_completed}), so consumers
 * can bind precisely to the jobs and lifecycle moments they care about.
 *
 * <p>Activated with {@code batch.admin.events.broker=rabbit} when a {@link RabbitTemplate} is on the
 * context. The message body is whatever the {@code RabbitTemplate}'s message converter produces;
 * configure a JSON converter for JSON payloads, otherwise the {@link BatchEvent} is serialized with
 * the default converter (it is {@link java.io.Serializable}).</p>
 */
public class RabbitBatchEventPublisher implements BatchEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitBatchEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKeyPrefix;

    public RabbitBatchEventPublisher(RabbitTemplate rabbitTemplate, String exchange, String routingKeyPrefix) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKeyPrefix = routingKeyPrefix;
    }

    @Override
    public void publish(BatchEvent event) {
        String routingKey = routingKey(event);
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
        } catch (AmqpException ex) {
            // A publisher must not break the job it observes; surface the failure in logs only.
            log.warn("[batch-admin] failed to publish event {} to exchange '{}' (routingKey '{}'): {}",
                    event.type(), exchange, routingKey, ex.getMessage());
        }
    }

    private String routingKey(BatchEvent event) {
        String job = event.jobName() == null ? "unknown" : event.jobName().replace('.', '_');
        return routingKeyPrefix + "." + job + "." + event.type().name().toLowerCase();
    }
}
