package com.bulkemail.pro.controller;

import com.bulkemail.pro.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Receives inbound webhook callbacks from ESP providers and enqueues them
 * for asynchronous processing by {@code WebhookInboundConsumer}.
 *
 * Endpoint:  POST /api/webhooks/inbound/{provider}
 *
 * Supported providers:
 *   sendgrid  — payload is a JSON array of event objects
 *   mailgun   — payload is a single event map
 *   generic   — any flat map with at least {event, email} keys
 *
 * The response is always HTTP 200 immediately — ESPs retry on non-2xx,
 * which would cause duplicate processing.  The consumer is idempotent
 * (duplicate events are silently ignored once suppressed/bounced).
 *
 * Security: in production, add HMAC signature verification per provider.
 * Use X-Twilio-Email-Event-Webhook-Signature for SendGrid,
 * X-Mailgun-Signature for Mailgun, etc.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Generic / SendGrid-style endpoint.
     * SendGrid sends an array; others send a single object.
     * We normalise both to individual messages on the queue.
     */
    @PostMapping("/inbound/{provider}")
    public ResponseEntity<Void> receiveWebhook(
            @PathVariable String provider,
            @RequestBody Object payload,
            @RequestHeader(value = "X-Organization-Id", required = false) Long orgId) {

        try {
            if (payload instanceof List<?> events) {
                // SendGrid-style: array of event objects
                for (Object event : events) {
                    if (event instanceof Map<?, ?> map) {
                        publish(normalise(map, provider, orgId));
                    }
                }
            } else if (payload instanceof Map<?, ?> map) {
                // Mailgun / generic: single event object
                publish(normalise(map, provider, orgId));
            } else {
                log.warn("Unrecognised webhook payload type from provider '{}': {}", provider, payload.getClass().getSimpleName());
            }
        } catch (Exception e) {
            // Always return 200 to prevent ESP retries flooding the endpoint
            log.error("Failed to enqueue webhook from provider '{}': {}", provider, e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void publish(Map<String, Object> canonical) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.WEBHOOK_INBOUND_QUEUE, canonical);
        log.debug("Enqueued webhook event: type={} email={}", canonical.get("event"), canonical.get("email"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalise(Map<?, ?> raw, String provider, Long orgId) {
        Map<String, Object> canonical = new HashMap<>((Map<String, Object>) raw);

        // Normalise provider-specific field names to canonical form
        switch (provider.toLowerCase()) {
            case "sendgrid" -> {
                // SendGrid: "event" → "bounce"/"complaint"/"unsubscribe"/"delivered"
                // "email" is already present
            }
            case "mailgun" -> {
                // Mailgun: "event" may be "failed", "complained", "unsubscribed"
                Object event = canonical.get("event");
                if ("failed".equals(event))       canonical.put("event", "bounce");
                if ("complained".equals(event))   canonical.put("event", "complaint");
                if ("unsubscribed".equals(event)) canonical.put("event", "unsubscribe");
                // Mailgun sends recipient in "recipient" field
                if (!canonical.containsKey("email") && canonical.containsKey("recipient")) {
                    canonical.put("email", canonical.get("recipient"));
                }
            }
        }

        canonical.put("provider", provider);
        if (orgId != null) canonical.put("organizationId", orgId);

        return canonical;
    }
}
