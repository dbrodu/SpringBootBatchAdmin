package io.batchadmin.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Default {@link BatchEventPublisher}: republishes each event as a Spring application event (so any
 * host bean can consume it with {@code @EventListener(BatchEvent.class)}) and logs it at INFO. This
 * keeps the pub/sub pattern available with zero infrastructure; a broker-backed publisher can be
 * swapped in via configuration.
 */
public class ApplicationBatchEventPublisher implements BatchEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ApplicationBatchEventPublisher.class);

    private final ApplicationEventPublisher delegate;

    public ApplicationBatchEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(BatchEvent event) {
        log.info("[batch-admin] event {} job={} executionId={} status={}",
                event.type(), event.jobName(), event.executionId(), event.status());
        delegate.publishEvent(event);
    }
}
