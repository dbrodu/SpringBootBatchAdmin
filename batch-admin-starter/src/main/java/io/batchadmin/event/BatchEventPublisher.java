package io.batchadmin.event;

/**
 * Publishes job-lifecycle {@link BatchEvent}s. The default implementation
 * ({@link ApplicationBatchEventPublisher}) fans events out as Spring application events and logs
 * them; an opt-in {@link RabbitBatchEventPublisher} forwards them to a RabbitMQ broker. Host
 * applications can supply their own bean to integrate any other transport.
 */
@FunctionalInterface
public interface BatchEventPublisher {

    /** Publishes a single lifecycle event. Implementations must not throw on transport errors. */
    void publish(BatchEvent event);
}
