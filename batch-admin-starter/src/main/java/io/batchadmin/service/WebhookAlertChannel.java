package io.batchadmin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.batchadmin.web.dto.AlertNotification;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delivers alerts by POSTing the notification as JSON to a configured URL — works with Slack/Teams
 * incoming webhooks or any HTTP receiver. Sent asynchronously with a short timeout so it never blocks
 * (or breaks) the job that was observed.
 */
public class WebhookAlertChannel implements AlertChannel {

    private static final Logger log = LoggerFactory.getLogger(WebhookAlertChannel.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebhookAlertChannel(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public AlertChannelType type() {
        return AlertChannelType.WEBHOOK;
    }

    @Override
    public void send(AlertNotification notification, String target) {
        if (target == null || target.isBlank()) {
            log.warn("[batch-admin][alert] webhook rule has no target URL; alert not delivered: {}",
                    notification.message());
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(notification);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target.trim()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        log.warn("[batch-admin][alert] webhook POST to {} failed: {}", target, ex.getMessage());
                        return null;
                    });
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("[batch-admin][alert] could not send webhook alert: {}", ex.getMessage());
        }
    }
}
