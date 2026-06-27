package io.batchadmin.service;

import io.batchadmin.web.dto.AlertNotification;

/**
 * Delivers a fired {@link AlertNotification} to some destination. The component ships a {@code LOG}
 * and a {@code WEBHOOK} channel; host applications can register their own bean (e.g. email, PagerDuty)
 * to add transports — it is selected by its {@link #type()}.
 */
public interface AlertChannel {

    /** The channel type this implementation handles. */
    AlertChannelType type();

    /**
     * Delivers the notification. The {@code target} is the rule's channel target (e.g. a webhook URL).
     * Implementations must not throw on transport errors — failure to deliver must never break the
     * job that was observed; log and return instead.
     */
    void send(AlertNotification notification, String target);
}
