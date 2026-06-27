package io.batchadmin.service;

import io.batchadmin.web.dto.AlertNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Writes alerts to the application log at WARN. Always available. */
public class LogAlertChannel implements AlertChannel {

    private static final Logger log = LoggerFactory.getLogger("io.batchadmin.alerts");

    @Override
    public AlertChannelType type() {
        return AlertChannelType.LOG;
    }

    @Override
    public void send(AlertNotification notification, String target) {
        log.warn("[batch-admin][alert] {}", notification.message());
    }
}
