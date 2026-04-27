package com.bulkemail.pro.service;

import com.bulkemail.pro.exception.SmtpDailyLimitException;
import com.bulkemail.pro.model.entity.*;
import com.bulkemail.pro.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchProcessor {

    private final EmailQueueRepository emailQueueRepository;
    private final CampaignRepository campaignRepository;
    private final ContactRepository contactRepository;
    private final EmailSenderService emailSenderService;
    private final MergeTagService mergeTagService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WarmupPlanRepository warmupPlanRepository;

    @Value("${app.tracking.base-url}")
    private String trackingBaseUrl;

    @Value("${app.unsubscribe.base-url:${app.tracking.base-url}}")
    private String unsubscribeBaseUrl;

    private final Map<Long, AtomicBoolean> pausedCampaigns = new ConcurrentHashMap<>();
    private final Map<Long, AtomicBoolean> cancelledCampaigns = new ConcurrentHashMap<>();

    @Async("emailTaskExecutor")
    public void processCampaign(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new RuntimeException("Campaign not found: " + campaignId));

        log.info("Starting campaign processing: {} ({})", campaign.getName(), campaignId);

        pausedCampaigns.put(campaignId, new AtomicBoolean(false));
        cancelledCampaigns.put(campaignId, new AtomicBoolean(false));

        campaign.setStatus(Campaign.CampaignStatus.SENDING);
        campaign.setStartedAt(LocalDateTime.now());
        campaignRepository.save(campaign);

        int batchSize = campaign.getBatchSize() != null ? campaign.getBatchSize() : 100;
        long batchDelay = campaign.getBatchDelaySeconds() != null ? campaign.getBatchDelaySeconds() * 1000L : 60000L;
        long interEmailDelay = campaign.getInterEmailDelayMs() != null ? campaign.getInterEmailDelayMs() : 200L;

        // Check warmup plan limit
        int warmupDayLimit = getWarmupDailyLimit(campaign);

        try {
            long totalPending = emailQueueRepository.countByCampaignIdAndStatus(
                campaignId, EmailQueue.QueueStatus.PENDING);
            long processed = 0;
            int sentThisRun = 0;

            while (true) {
                if (cancelledCampaigns.get(campaignId).get()) {
                    emailQueueRepository.cancelPendingByCampaign(campaignId);
                    campaign.setStatus(Campaign.CampaignStatus.CANCELLED);
                    break;
                }

                while (pausedCampaigns.get(campaignId).get()) {
                    if (cancelledCampaigns.get(campaignId).get()) break;
                    Thread.sleep(1000);
                }

                // Warmup limit: stop if daily limit reached
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
                    if (cancelledCampaigns.get(campaignId).get()) break;

                    while (pausedCampaigns.get(campaignId).get()) {
                        if (cancelledCampaigns.get(campaignId).get()) break;
                        Thread.sleep(1000);
                    }

                    try {
                        emailSenderService.sendEmail(item);
                        sentThisRun++;
                    } catch (SmtpDailyLimitException e) {
                        // SMTP daily/hourly limit hit — pause campaign; emails stay PENDING
                        log.warn("SMTP limit reached for campaign {}. Pausing.", campaignId);
                        campaign = campaignRepository.findById(campaignId).orElse(campaign);
                        campaign.setStatus(Campaign.CampaignStatus.PAUSED);
                        campaignRepository.save(campaign);
                        sendStatusMessage(campaignId,
                            "Paused — daily limit reached. Remaining emails will resume when limit resets.");
                        return;
                    }

                    processed++;
                    sendProgressUpdate(campaignId, processed, totalPending, campaign.getName());

                    if (interEmailDelay > 0) {
                        Thread.sleep(interEmailDelay);
                    }
                }

                long remaining = emailQueueRepository.countByCampaignIdAndStatus(
                    campaignId, EmailQueue.QueueStatus.PENDING);

                if (remaining == 0) break;

                if (batchDelay > 0) {
                    log.info("Batch complete. Waiting {}ms before next batch. {} remaining.", batchDelay, remaining);
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
            pausedCampaigns.remove(campaignId);
            cancelledCampaigns.remove(campaignId);
        }
    }

    public void buildQueue(Campaign campaign, List<Contact> contacts) {
        List<EmailQueue> queueItems = new ArrayList<>();

        for (Contact contact : contacts) {
            if (contact.getStatus() != Contact.ContactStatus.ACTIVE) continue;

            String trackingId = UUID.randomUUID().toString();
            String unsubscribeUrl = unsubscribeBaseUrl + "/unsubscribe/" + contact.getUnsubscribeToken();

            String html = mergeTagService.merge(
                campaign.getHtmlContent(), contact, unsubscribeUrl, trackingBaseUrl);

            if (campaign.isTrackOpens()) {
                html = mergeTagService.addTrackingPixel(html, trackingId, trackingBaseUrl);
            }
            if (campaign.isTrackClicks()) {
                html = mergeTagService.wrapLinks(html, trackingId, trackingBaseUrl);
            }

            String subject = mergeTagService.mergeSubject(campaign.getSubject(), contact);

            // Auto-generate plain text when not provided
            String rawText = campaign.getTextContent();
            String text;
            if (rawText != null && !rawText.isBlank()) {
                text = mergeTagService.merge(rawText, contact, unsubscribeUrl, "");
            } else {
                text = htmlToPlainText(html) + "\n\nTo unsubscribe: " + unsubscribeUrl;
            }

            EmailQueue item = new EmailQueue();
            item.setCampaign(campaign);
            item.setContact(contact);
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

    private int getWarmupDailyLimit(Campaign campaign) {
        if (campaign.getSmtpConfig() == null) return 0;
        return warmupPlanRepository.findActiveBySmtpConfigId(campaign.getSmtpConfig().getId())
            .map(WarmupPlan::getCurrentDayVolume)
            .orElse(0);
    }

    private void sendProgressUpdate(Long campaignId, long processed, long total, String name) {
        try {
            Map<String, Object> progress = new HashMap<>();
            progress.put("campaignId", campaignId);
            progress.put("campaignName", name);
            progress.put("processed", processed);
            progress.put("total", total);
            progress.put("percentage", total > 0 ? (double) processed / total * 100 : 0);
            messagingTemplate.convertAndSend("/topic/campaign-progress/" + campaignId, progress);
        } catch (Exception e) {
            log.debug("Could not send WebSocket progress update: {}", e.getMessage());
        }
    }

    private void sendStatusMessage(Long campaignId, String message) {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("campaignId", campaignId);
            status.put("statusMessage", message);
            messagingTemplate.convertAndSend("/topic/campaign-progress/" + campaignId, status);
        } catch (Exception ignored) {}
    }

    public void pauseCampaign(Long campaignId) {
        AtomicBoolean paused = pausedCampaigns.get(campaignId);
        if (paused != null) {
            paused.set(true);
        }
        Campaign campaign = campaignRepository.findById(campaignId).orElseThrow();
        campaign.setStatus(Campaign.CampaignStatus.PAUSED);
        campaignRepository.save(campaign);
    }

    public void resumeCampaign(Long campaignId) {
        AtomicBoolean paused = pausedCampaigns.get(campaignId);
        if (paused != null) {
            paused.set(false);
            Campaign campaign = campaignRepository.findById(campaignId).orElseThrow();
            campaign.setStatus(Campaign.CampaignStatus.SENDING);
            campaignRepository.save(campaign);
        } else {
            // Not in memory (app restarted) — re-trigger async processing from remaining PENDING items
            processCampaign(campaignId);
        }
    }

    public void cancelCampaign(Long campaignId) {
        AtomicBoolean cancelled = cancelledCampaigns.get(campaignId);
        if (cancelled != null) {
            cancelled.set(true);
        } else {
            emailQueueRepository.cancelPendingByCampaign(campaignId);
            Campaign campaign = campaignRepository.findById(campaignId).orElseThrow();
            campaign.setStatus(Campaign.CampaignStatus.CANCELLED);
            campaignRepository.save(campaign);
        }
    }
}
