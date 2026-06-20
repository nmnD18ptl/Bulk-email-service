package com.bulkemail.pro.messaging;

import com.bulkemail.pro.config.RabbitMqConfig;
import com.bulkemail.pro.model.entity.Contact;
import com.bulkemail.pro.model.entity.SuppressionList;
import com.bulkemail.pro.repository.ContactRepository;
import com.bulkemail.pro.repository.SuppressionListRepository;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Processes inbound webhook events from ESP providers (SendGrid, Mailgun, SES, etc.).
 *
 * The WebhookController receives the raw HTTP callback, normalises the payload
 * into a canonical map, and publishes it to the webhook.inbound queue.
 * This consumer then applies the business rules (update contact status,
 * add to suppression list) asynchronously without blocking the HTTP response.
 *
 * Supported event types (normalised):
 *   bounce    — hard bounce: mark contact BOUNCED + suppress
 *   complaint — spam complaint: mark contact COMPLAINED + suppress
 *   unsubscribe — one-click unsubscribe: mark contact UNSUBSCRIBED + suppress
 *   delivered   — record delivery (no state change, future analytics use)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookInboundConsumer {

    private final ContactRepository      contactRepository;
    private final SuppressionListRepository suppressionListRepository;

    @RabbitListener(queues = RabbitMqConfig.WEBHOOK_INBOUND_QUEUE)
    @Transactional
    public void handle(Map<String, Object> payload,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {
        try {
            String event = normalise(payload.get("event"));
            String email = normalise(payload.get("email"));
            Long   orgId = extractOrgId(payload);

            if (email == null || email.isBlank()) {
                log.warn("Webhook event '{}' has no email — discarding", event);
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.info("Processing webhook: event={} email={} org={}", event, email, orgId);

            switch (event != null ? event.toLowerCase() : "") {
                case "bounce", "hard_bounce", "hardbounce" -> handleBounce(email, orgId, payload);
                case "complaint", "spam", "spamreport"     -> handleComplaint(email, orgId);
                case "unsubscribe", "optout"               -> handleUnsubscribe(email, orgId);
                case "delivered"                           -> log.debug("Delivery confirmed for {}", email);
                default -> log.debug("Unhandled webhook event type '{}' for {} — skipping", event, email);
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to process webhook payload: {}", e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false); // → DLQ after retries
        }
    }

    // ── Event handlers ────────────────────────────────────────────────

    private void handleBounce(String email, Long orgId, Map<String, Object> payload) {
        String reason = normalise(payload.getOrDefault("reason", "Hard bounce"));
        updateContactStatus(email, orgId, Contact.ContactStatus.BOUNCED);
        addToSuppressionList(email, orgId, SuppressionList.Reason.BOUNCE, reason);
    }

    private void handleComplaint(String email, Long orgId) {
        updateContactStatus(email, orgId, Contact.ContactStatus.COMPLAINED);
        addToSuppressionList(email, orgId, SuppressionList.Reason.COMPLAINT, "Spam complaint");
    }

    private void handleUnsubscribe(String email, Long orgId) {
        updateContactStatus(email, orgId, Contact.ContactStatus.UNSUBSCRIBED);
        addToSuppressionList(email, orgId, SuppressionList.Reason.UNSUBSCRIBE, "ESP unsubscribe");
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void updateContactStatus(String email, Long orgId, Contact.ContactStatus status) {
        if (orgId != null) {
            contactRepository.findByOrganizationIdAndEmailIgnoreCase(orgId, email)
                    .ifPresent(c -> { c.setStatus(status); contactRepository.save(c); });
        } else {
            contactRepository.findByEmail(email)
                    .ifPresent(c -> { c.setStatus(status); contactRepository.save(c); });
        }
    }

    private void addToSuppressionList(String email, Long orgId,
                                      SuppressionList.Reason reason, String notes) {
        boolean alreadySuppressed = orgId != null
                ? suppressionListRepository.existsByOrganizationIdAndEmailIgnoreCase(orgId, email)
                : suppressionListRepository.existsByOrganizationIdIsNullAndEmailIgnoreCase(email);

        if (!alreadySuppressed) {
            // Constructor signature: (Long organizationId, String email, Reason reason, String source)
            SuppressionList entry = new SuppressionList(orgId, email, reason, notes);
            suppressionListRepository.save(entry);
            log.info("Suppressed {} (org={}) reason={}", email, orgId, reason);
        }
    }

    private String normalise(Object value) {
        return value != null ? value.toString().trim() : null;
    }

    private Long extractOrgId(Map<String, Object> payload) {
        Object raw = payload.get("organizationId");
        if (raw instanceof Number) return ((Number) raw).longValue();
        if (raw instanceof String) {
            try { return Long.parseLong((String) raw); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
