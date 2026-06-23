package com.bulkemail.pro.messaging;

import com.bulkemail.pro.config.RabbitMqConfig;
import com.bulkemail.pro.security.TenantContext;
import com.bulkemail.pro.service.BatchProcessor;
import com.bulkemail.pro.service.DistributedLockService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes CAMPAIGN_SEND_REQUESTED messages from the campaign.trigger queue.
 *
 * Before processing, acquires a per-campaign distributed lock so that only
 * one app instance can process a given campaign at a time, even when the
 * application is horizontally scaled.  The lock TTL is set to 4 hours — if
 * the consumer crashes, the lock expires and the message can be requeued.
 *
 * Idempotency: if the same campaignId arrives twice (e.g. after a crash
 * during processing), the lock prevents a duplicate run.  The email_queue
 * table is the authoritative state; only PENDING rows are processed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignTriggerConsumer {

    private static final long CAMPAIGN_LOCK_TTL_MS = 4 * 60 * 60 * 1_000L; // 4 hours

    private final BatchProcessor        batchProcessor;
    private final DistributedLockService lockService;

    @RabbitListener(queues = RabbitMqConfig.CAMPAIGN_TRIGGER_QUEUE)
    public void handle(Map<String, Object> payload,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {

        Long campaignId = extractCampaignId(payload);
        if (campaignId == null) {
            log.error("Received CAMPAIGN_SEND_REQUESTED with missing campaignId — rejecting");
            channel.basicReject(deliveryTag, false); // don't requeue — goes to DLQ
            return;
        }

        String lockValue = lockService.newLockValue();
        String lockKey   = "campaign:" + campaignId;

        if (!lockService.acquire(lockKey, lockValue, CAMPAIGN_LOCK_TTL_MS)) {
            log.warn("Campaign {} is already being processed by another instance — acking to discard duplicate", campaignId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        Long orgId = extractOrgId(payload);
        try {
            if (orgId != null) TenantContext.set(orgId, null, null);
            log.info("Acquired lock for campaign {} (org={}) — starting processing", campaignId, orgId);
            batchProcessor.processCampaignSync(campaignId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Campaign {} processing failed: {}", campaignId, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        } finally {
            TenantContext.clear();
            lockService.release(lockKey, lockValue);
        }
    }

    private Long extractCampaignId(Map<String, Object> payload) {
        Object raw = payload.get("campaignId");
        if (raw instanceof Number) return ((Number) raw).longValue();
        if (raw instanceof String) {
            try { return Long.parseLong((String) raw); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Long extractOrgId(Map<String, Object> payload) {
        Object raw = payload.get("organizationId");
        if (raw instanceof Number) {
            long v = ((Number) raw).longValue();
            return v > 0 ? v : null;
        }
        return null;
    }
}
