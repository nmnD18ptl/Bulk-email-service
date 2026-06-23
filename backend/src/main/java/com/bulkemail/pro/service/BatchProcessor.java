package com.bulkemail.pro.service;

import com.bulkemail.pro.exception.SmtpDailyLimitException;
import com.bulkemail.pro.model.entity.*;
import com.bulkemail.pro.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Processes bulk email campaigns batch-by-batch.
 *
 * Entry point is {@link #processCampaignSync(Long)}, called by
 * {@code CampaignTriggerConsumer} after it acquires the campaign-scoped
 * distributed lock.  The @Async annotation has been removed — concurrency
 * control is now the consumer's responsibility (via the distributed lock),
 * which works correctly under horizontal scaling.
 *
 * Pause / resume / cancel state is stored in Redis rather than in-memory
 * ConcurrentHashMaps, so any app instance can pause a campaign that another
 * instance is currently processing.
 *
 * Redis keys:
 *   campaign:paused:{id}    — SET = paused, absent = running
 *   campaign:cancelled:{id} — SET = cancelled, absent = running
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchProcessor {

    private final EmailQueueRepository    emailQueueRepository;
    private final CampaignRepository      campaignRepository;
    private final ContactRepository       contactRepository;
    private final EmailSenderService      emailSenderService;
    private final MergeTagService         mergeTagService;
    private final SimpMessagingTemplate   messagingTemplate;
    private final WarmupPlanRepository    warmupPlanRepository;
    private final StringRedisTemplate     redis;

    @Value("${app.tracking.base-url}")
    private String trackingBaseUrl;

    @Value("${app.unsubscribe.base-url:${app.tracking.base-url}}")
    private String unsubscribeBaseUrl;

    // ── Redis key patterns ────────────────────────────────────

    private static String pauseKey(Long id)    { return "campaign:paused:"    + id; }
    private static String cancelKey(Long id)   { return "campaign:cancelled:" + id; }

    // ── Public control API (called by CampaignService) ────────

    public void pauseCampaign(Long campaignId) {
        redis.opsForValue().set(pauseKey(campaignId), "1");
        campaignRepository.findById(campaignId).ifPresent(c -> {
            c.setStatus(Campaign.CampaignStatus.PAUSED);
            campaignRepository.save(c);
        });
    }

    public void resumeCampaign(Long campaignId) {
        redis.delete(pauseKey(campaignId));
        campaignRepository.findById(campaignId).ifPresent(c -> {
            c.setStatus(Campaign.CampaignStatus.SENDING);
            campaignRepository.save(c);
        });
    }

    public void cancelCampaign(Long campaignId) {
        redis.opsForValue().set(cancelKey(campaignId), "1");
        emailQueueRepository.cancelPendingByCampaign(campaignId);
        campaignRepository.findById(campaignId).ifPresent(c -> {
            c.setStatus(Campaign.CampaignStatus.CANCELLED);
            campaignRepository.save(c);
        });
    }

    // ── Main processing method ────────────────────────────────

    /**
     * Processes all PENDING emails for the given campaign.
     * Must be called from within the campaign-scoped distributed lock.
     */
    public void processCampaignSync(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campaign not found: " + campaignId));

        log.info("Starting campaign processing: {} ({})", campaign.getName(), campaignId);

        campaign.setStatus(Campaign.CampaignStatus.SENDING);
        campaign.setStartedAt(LocalDateTime.now());
        campaignRepository.save(campaign);

        int  batchSize       = campaign.getBatchSize()             != null ? campaign.getBatchSize()             : 100;
        long batchDelay      = campaign.getBatchDelaySeconds()     != null ? campaign.getBatchDelaySeconds() * 1000L : 60_000L;
        long interEmailDelay = campaign.getInterEmailDelayMs()     != null ? campaign.getInterEmailDelayMs()     : 200L;
        int  warmupDayLimit  = getWarmupDailyLimit(campaign);

        try {
            long totalPending = emailQueueRepository.countByCampaignIdAndStatus(
                    campaignId, EmailQueue.QueueStatus.PENDING);
            long processed    = 0;
            int  sentThisRun  = 0;

            while (true) {
                if (isCancelled(campaignId)) {
                    campaign.setStatus(Campaign.CampaignStatus.CANCELLED);
                    break;
                }

                waitWhilePaused(campaignId);

                if (warmupDayLimit > 0 && sentThisRun >= warmupDayLimit) {
                    log.info("Warmup daily limit ({}) reached for campaign {}", warmupDayLimit, campaignId);
                    campaign = campaignRepository.findById(campaignId).orElse(campaign);
                    campaign.setStatus(Campaign.CampaignStatus.PAUSED);
                    campaignRepository.save(campaign);
                    sendStatusMessage(campaignId, "Warmup limit reached (" + warmupDayLimit + "/day). Will resume tomorrow.");
                    return;
                }

                List<EmailQueue> batch = emailQueueRepository.findPendingByCampaign(
                        campaignId, PageRequest.of(0, batchSize));

                if (batch.isEmpty()) break;

                log.info("Processing batch of {} emails for campaign {}", batch.size(), campaignId);

                for (EmailQueue item : batch) {
                    if (isCancelled(campaignId)) break;
                    waitWhilePaused(campaignId);

                    try {
                        boolean sent = emailSenderService.sendEmail(item);
                        if (sent) sentThisRun++;
                    } catch (SmtpDailyLimitException e) {
                        log.warn("SMTP limit reached for campaign {}. Pausing.", campaignId);
                        campaign = campaignRepository.findById(campaignId).orElse(campaign);
                        campaign.setStatus(Campaign.CampaignStatus.PAUSED);
                        campaignRepository.save(campaign);
                        sendStatusMessage(campaignId,
                                "Paused — daily limit reached. Remaining emails will resume when limit resets.");
                        return;
                    } catch (Exception e) {
                        log.error("Unexpected error sending to {} for campaign {}: {}",
                                item.getRecipientEmail(), campaignId, e.getMessage(), e);
                        item.setRetryCount(item.getRetryCount() != null ? item.getRetryCount() + 1 : 1);
                        item.setLastAttemptAt(LocalDateTime.now());
                        item.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
                        if (item.getRetryCount() >= (item.getMaxRetries() != null ? item.getMaxRetries() : 3)) {
                            item.setStatus(EmailQueue.QueueStatus.FAILED);
                        } else {
                            item.setStatus(EmailQueue.QueueStatus.PENDING);
                        }
                        emailQueueRepository.save(item);
                    }

                    processed++;
                    sendProgressUpdate(campaignId, processed, totalPending, campaign.getName());

                    if (interEmailDelay > 0) Thread.sleep(interEmailDelay);
                }

                long remaining = emailQueueRepository.countByCampaignIdAndStatus(
                        campaignId, EmailQueue.QueueStatus.PENDING);

                if (remaining == 0) break;

                if (batchDelay > 0) {
                    log.info("Batch complete. Waiting {}ms. {} remaining.", batchDelay, remaining);
                    Thread.sleep(batchDelay);
                }
            }

            campaign = campaignRepository.findById(campaignId).orElse(campaign);
            if (campaign.getStatus() != Campaign.CampaignStatus.CANCELLED &&
                campaign.getStatus() != Campaign.CampaignStatus.PAUSED) {
                campaign.setStatus(Campaign.CampaignStatus.COMPLETED);
            }
            campaign.setCompletedAt(LocalDateTime.now());
            campaignRepository.save(campaign);

            log.info("Campaign {} completed. Sent: {}, Failed: {}",
                    campaignId, campaign.getSentCount(), campaign.getFailedCount());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Campaign processing interrupted: {}", campaignId);
            campaign.setStatus(Campaign.CampaignStatus.FAILED);
            campaignRepository.save(campaign);
        } catch (Exception e) {
            log.error("Campaign processing failed: {}", e.getMessage(), e);
            campaign.setStatus(Campaign.CampaignStatus.FAILED);
            campaignRepository.save(campaign);
        } finally {
            // Clean up Redis flags — no longer needed after processing ends
            redis.delete(pauseKey(campaignId));
            redis.delete(cancelKey(campaignId));
        }
    }

    // ── Queue builder ─────────────────────────────────────────

    public void buildQueue(Campaign campaign, List<Contact> contacts) {
        List<EmailQueue> queueItems = new ArrayList<>();

        for (Contact contact : contacts) {
            if (contact.getStatus() != Contact.ContactStatus.ACTIVE) continue;

            String trackingId    = UUID.randomUUID().toString();
            String unsubscribeUrl = unsubscribeBaseUrl + "/unsubscribe/" + contact.getUnsubscribeToken()
                    + "?c=" + campaign.getId();

            String html = mergeTagService.merge(
                    campaign.getHtmlContent(), contact, unsubscribeUrl, trackingBaseUrl);

            if (campaign.isTrackOpens())
                html = mergeTagService.addTrackingPixel(html, trackingId, trackingBaseUrl);
            if (campaign.isTrackClicks())
                html = mergeTagService.wrapLinks(html, trackingId, trackingBaseUrl);

            String subject = mergeTagService.mergeSubject(campaign.getSubject(), contact);

            String rawText = campaign.getTextContent();
            String text = (rawText != null && !rawText.isBlank())
                    ? mergeTagService.merge(rawText, contact, unsubscribeUrl, "")
                    : htmlToPlainText(html) + "\n\nTo unsubscribe: " + unsubscribeUrl;

            EmailQueue item = new EmailQueue();
            item.setCampaign(campaign);
            item.setContact(contact);
            item.setOrganizationId(campaign.getOrganizationId());
            item.setRecipientEmail(contact.getEmail());
            item.setRecipientName(contact.getFullName());
            item.setPersonalizedSubject(subject);
            item.setPersonalizedHtml(html);
            item.setPersonalizedText(text);
            item.setTrackingId(trackingId);
            item.setMaxRetries(campaign.getMaxRetries() != null ? campaign.getMaxRetries() : 3);

            queueItems.add(item);
        }

        emailQueueRepository.saveAll(queueItems);
        campaign.setTotalRecipients(queueItems.size());
        campaignRepository.save(campaign);
    }

    // ── Private helpers ───────────────────────────────────────

    private boolean isPaused(Long id)    { return Boolean.TRUE.equals(redis.hasKey(pauseKey(id))); }
    private boolean isCancelled(Long id) { return Boolean.TRUE.equals(redis.hasKey(cancelKey(id))); }

    private void waitWhilePaused(Long campaignId) throws InterruptedException {
        while (isPaused(campaignId)) {
            if (isCancelled(campaignId)) return;
            Thread.sleep(1_000);
        }
    }

    private int getWarmupDailyLimit(Campaign campaign) {
        if (campaign.getSmtpConfig() == null) return 0;
        return warmupPlanRepository.findActiveBySmtpConfigId(campaign.getSmtpConfig().getId())
                .map(WarmupPlan::getCurrentDayVolume)
                .orElse(0);
    }

    private void sendProgressUpdate(Long campaignId, long processed, long total, String name) {
        try {
            Map<String, Object> progress = new HashMap<>();
            progress.put("campaignId",   campaignId);
            progress.put("campaignName", name);
            progress.put("processed",    processed);
            progress.put("total",        total);
            progress.put("percentage",   total > 0 ? (double) processed / total * 100 : 0);
            messagingTemplate.convertAndSend("/topic/campaign-progress/" + campaignId, progress);
        } catch (Exception e) {
            log.debug("Could not send WebSocket progress update: {}", e.getMessage());
        }
    }

    private void sendStatusMessage(Long campaignId, String message) {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("campaignId",    campaignId);
            status.put("statusMessage", message);
            messagingTemplate.convertAndSend("/topic/campaign-progress/" + campaignId, status);
        } catch (Exception ignored) {}
    }

    private String htmlToPlainText(String html) {
        if (html == null) return "";
        return html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)<p[^>]*>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)<li[^>]*>", "\n• ")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;|&apos;", "'")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
