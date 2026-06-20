package com.bulkemail.pro.service;

import com.bulkemail.pro.config.RabbitMqConfig;
import com.bulkemail.pro.model.entity.OutboxEvent;
import com.bulkemail.pro.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Transactional Outbox relay.
 *
 * Polls the outbox_events table every second for PENDING rows and publishes
 * them to RabbitMQ.  Because the outbox row was written in the same DB
 * transaction as the domain change, this pattern guarantees at-least-once
 * delivery with no dual-write race condition.
 *
 * If RabbitMQ is unavailable the row stays PENDING and will be retried
 * on the next scheduled tick.  Failed rows (after 3 attempts) are marked
 * FAILED and caught by the retry poller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate        rabbitTemplate;
    private final DistributedLockService lockService;

    private static final String RELAY_LOCK     = "outbox:relay";
    private static final long   RELAY_LOCK_TTL = 30_000L; // 30 s

    @Scheduled(fixedDelay = 1_000)   // 1-second polling interval
    @Transactional
    public void relay() {
        String lockId = lockService.newLockValue();
        if (!lockService.acquire(RELAY_LOCK, lockId, RELAY_LOCK_TTL)) {
            return; // another instance is relaying — skip
        }

        try {
            List<OutboxEvent> pending = outboxEventRepository.findPendingBatch();
            if (pending.isEmpty()) return;

            log.debug("Relaying {} outbox events to RabbitMQ", pending.size());

            for (OutboxEvent event : pending) {
                try {
                    String routingKey = resolveRoutingKey(event.getEventType());
                    rabbitTemplate.convertAndSend(routingKey, event.getPayload());
                    event.markPublished();
                } catch (Exception e) {
                    log.warn("Failed to relay outbox event {} ({}): {}", event.getId(), event.getEventType(), e.getMessage());
                    event.markFailed(e.getMessage());
                }
                outboxEventRepository.save(event);
            }
        } finally {
            lockService.release(RELAY_LOCK, lockId);
        }
    }

    /** Retry events that previously failed (up to 3 times total). */
    @Scheduled(fixedDelay = 30_000)  // retry every 30 s
    @Transactional
    public void retryFailed() {
        List<OutboxEvent> retryable = outboxEventRepository.findRetryableFailed();
        if (retryable.isEmpty()) return;

        log.info("Retrying {} failed outbox events", retryable.size());
        retryable.forEach(OutboxEvent::resetToPending);
        outboxEventRepository.saveAll(retryable);
    }

    private String resolveRoutingKey(String eventType) {
        return switch (eventType) {
            case "CAMPAIGN_SEND_REQUESTED" -> RabbitMqConfig.CAMPAIGN_TRIGGER_QUEUE;
            case "EMAIL_SEND"              -> RabbitMqConfig.EMAIL_SEND_QUEUE;
            case "WEBHOOK_INBOUND"         -> RabbitMqConfig.WEBHOOK_INBOUND_QUEUE;
            default -> throw new IllegalArgumentException("Unknown outbox event type: " + eventType);
        };
    }
}
