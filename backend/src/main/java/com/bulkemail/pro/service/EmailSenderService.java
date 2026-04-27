package com.bulkemail.pro.service;

import com.bulkemail.pro.exception.SmtpDailyLimitException;
import com.bulkemail.pro.model.entity.*;
import com.bulkemail.pro.repository.*;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSenderService {

    private final SmtpConfigService smtpConfigService;
    private final EmailQueueRepository emailQueueRepository;
    private final EmailTrackingRepository emailTrackingRepository;
    private final CampaignRepository campaignRepository;
    private final SmtpConfigRepository smtpConfigRepository;
    private final ContactRepository contactRepository;

    @Value("${app.tracking.base-url}")
    private String trackingBaseUrl;

    @Transactional
    public boolean sendEmail(EmailQueue queueItem) {
        Long campaignId = queueItem.getCampaign().getId();
        Campaign campaign = campaignRepository.findByIdWithSmtpConfig(campaignId)
                .orElseThrow(() -> new IllegalStateException("Campaign not found: " + campaignId));

        Contact contact = contactRepository.findById(queueItem.getContact().getId())
                .orElseThrow(() -> new IllegalStateException("Contact not found: " + queueItem.getContact().getId()));

        SmtpConfig smtpConfig = campaign.getSmtpConfig();
        if (smtpConfig == null) {
            smtpConfig = smtpConfigService.getDefault().orElse(null);
        }

        if (smtpConfig == null) {
            log.error("No SMTP config available for campaign {}", campaign.getId());
            queueItem.setStatus(EmailQueue.QueueStatus.FAILED);
            queueItem.setErrorMessage("No SMTP configuration available");
            emailQueueRepository.save(queueItem);
            return false;
        }

        if (!smtpConfigService.canSendMore(smtpConfig)) {
            log.warn("SMTP daily/hourly limit reached for config {}", smtpConfig.getName());
            // Keep as PENDING so the campaign can resume tomorrow; signal caller to pause
            throw new SmtpDailyLimitException("Daily/hourly sending limit reached for: " + smtpConfig.getName());
        }

        try {
            JavaMailSenderImpl mailSender = smtpConfigService.buildMailSender(smtpConfig);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String fromEmail = campaign.getFromEmail() != null ? campaign.getFromEmail() : smtpConfig.getFromEmail();
            String fromName  = campaign.getFromName()  != null ? campaign.getFromName()  : smtpConfig.getFromName();
            String fromDomain = fromEmail != null && fromEmail.contains("@")
                    ? fromEmail.substring(fromEmail.indexOf('@') + 1) : "mail.local";

            helper.setFrom(fromEmail, fromName != null ? fromName : fromEmail);
            helper.setTo(queueItem.getRecipientEmail());
            helper.setSubject(queueItem.getPersonalizedSubject());

            if (campaign.getReplyToEmail() != null) {
                helper.setReplyTo(campaign.getReplyToEmail());
            }

            String html = queueItem.getPersonalizedHtml();
            String text = queueItem.getPersonalizedText();

            if (html == null || html.isBlank()) {
                log.error("Campaign {} has no HTML content — cannot send email", campaign.getId());
                queueItem.setStatus(EmailQueue.QueueStatus.FAILED);
                queueItem.setErrorMessage("Campaign has no email body.");
                emailQueueRepository.save(queueItem);
                return false;
            }

            if (text != null && !text.isBlank()) {
                helper.setText(text, html);
            } else {
                helper.setText(html, true);
            }

            // RFC-compliant headers required by Gmail/Yahoo
            String unsubscribeUrl = trackingBaseUrl + "/unsubscribe/" + contact.getUnsubscribeToken();
            String replyToEmail = campaign.getReplyToEmail() != null ? campaign.getReplyToEmail() : fromEmail;
            message.setHeader("List-Unsubscribe",
                "<mailto:" + replyToEmail + "?subject=unsubscribe>, <" + unsubscribeUrl + ">");
            message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            message.setHeader("Precedence", "bulk");
            message.setHeader("Feedback-ID",
                campaign.getId() + ":" + fromDomain + ":campaign:" + fromDomain);
            message.setHeader("X-Entity-Ref-ID", UUID.randomUUID().toString());

            String messageId = UUID.randomUUID().toString() + "@" + fromDomain;
            message.setHeader("Message-ID", "<" + messageId + ">");

            // Remove JavaMail default X-Mailer header
            message.removeHeader("X-Mailer");

            mailSender.send(message);

            queueItem.setStatus(EmailQueue.QueueStatus.SENT);
            queueItem.setSentAt(LocalDateTime.now());
            queueItem.setMessageId(messageId);
            emailQueueRepository.save(queueItem);

            recordTracking(queueItem, campaign, contact, EmailTracking.TrackingEvent.SENT, null);
            smtpConfigService.incrementSentCount(smtpConfig);
            updateCampaignStats(campaign, "sent");

            log.debug("Email sent to {} for campaign {}", queueItem.getRecipientEmail(), campaign.getId());
            return true;

        } catch (MailSendException e) {
            // Detect hard bounce (5xx SMTP codes)
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            boolean isHardBounce = errorMsg.contains("550") || errorMsg.contains("551") ||
                    errorMsg.contains("552") || errorMsg.contains("553") || errorMsg.contains("554") ||
                    errorMsg.toLowerCase().contains("user unknown") ||
                    errorMsg.toLowerCase().contains("no such user") ||
                    errorMsg.toLowerCase().contains("does not exist") ||
                    errorMsg.toLowerCase().contains("invalid address");

            log.error("MailSendException for {} campaign {}: {}",
                queueItem.getRecipientEmail(), campaign.getId(), errorMsg);

            if (isHardBounce) {
                queueItem.setStatus(EmailQueue.QueueStatus.BOUNCED);
                queueItem.setErrorMessage("Hard bounce: " + errorMsg);
                emailQueueRepository.save(queueItem);
                contact.setStatus(Contact.ContactStatus.BOUNCED);
                contactRepository.save(contact);
                updateCampaignStats(campaign, "bounce");
            } else {
                // Soft bounce — retry
                queueItem.setRetryCount(queueItem.getRetryCount() + 1);
                queueItem.setLastAttemptAt(LocalDateTime.now());
                queueItem.setErrorMessage(errorMsg);
                if (queueItem.getRetryCount() >= queueItem.getMaxRetries()) {
                    queueItem.setStatus(EmailQueue.QueueStatus.FAILED);
                    updateCampaignStats(campaign, "failed");
                } else {
                    queueItem.setStatus(EmailQueue.QueueStatus.PENDING);
                }
                emailQueueRepository.save(queueItem);
            }
            return false;

        } catch (Exception e) {
            log.error("Failed to send email to {} for campaign {}: {}",
                queueItem.getRecipientEmail(), campaign.getId(), e.getMessage());

            queueItem.setRetryCount(queueItem.getRetryCount() + 1);
            queueItem.setLastAttemptAt(LocalDateTime.now());
            queueItem.setErrorMessage(e.getMessage());

            if (queueItem.getRetryCount() >= queueItem.getMaxRetries()) {
                queueItem.setStatus(EmailQueue.QueueStatus.FAILED);
                updateCampaignStats(campaign, "failed");
            } else {
                queueItem.setStatus(EmailQueue.QueueStatus.PENDING);
            }

            emailQueueRepository.save(queueItem);
            return false;
        }
    }

    private void recordTracking(EmailQueue queueItem, Campaign campaign, Contact contact,
                                EmailTracking.TrackingEvent event, String url) {
        EmailTracking tracking = new EmailTracking();
        tracking.setTrackingId(queueItem.getTrackingId());
        tracking.setCampaign(campaign);
        tracking.setContact(contact);
        tracking.setEmailQueue(queueItem);
        tracking.setEventType(event);
        tracking.setOriginalUrl(url);
        emailTrackingRepository.save(tracking);
    }

    private void updateCampaignStats(Campaign campaign, String type) {
        Campaign managed = campaignRepository.findById(campaign.getId()).orElse(campaign);
        switch (type) {
            case "sent"        -> managed.setSentCount(managed.getSentCount() + 1);
            case "failed"      -> managed.setFailedCount(managed.getFailedCount() + 1);
            case "open"        -> managed.setOpenCount(managed.getOpenCount() + 1);
            case "click"       -> managed.setClickCount(managed.getClickCount() + 1);
            case "bounce"      -> managed.setBounceCount(managed.getBounceCount() + 1);
            case "unsubscribe" -> managed.setUnsubscribeCount(managed.getUnsubscribeCount() + 1);
        }
        campaignRepository.save(managed);
    }

    @Transactional
    public void recordOpen(String trackingId, String ipAddress, String userAgent) {
        emailQueueRepository.findByTrackingId(trackingId).ifPresent(queue -> {
            boolean firstOpen = !emailTrackingRepository.existsByTrackingIdAndEventType(
                trackingId, EmailTracking.TrackingEvent.OPENED);

            EmailTracking tracking = new EmailTracking();
            tracking.setTrackingId(trackingId + "_open_" + UUID.randomUUID());
            tracking.setCampaign(queue.getCampaign());
            tracking.setContact(queue.getContact());
            tracking.setEmailQueue(queue);
            tracking.setEventType(EmailTracking.TrackingEvent.OPENED);
            tracking.setIpAddress(ipAddress);
            tracking.setUserAgent(userAgent);
            tracking.setFirstOpen(firstOpen);
            emailTrackingRepository.save(tracking);

            if (firstOpen) {
                updateCampaignStats(queue.getCampaign(), "open");
                Contact contact = queue.getContact();
                contact.setLastOpenedAt(LocalDateTime.now());
                contact.setEngagementScore(contact.getEngagementScore() + 5);
                contactRepository.save(contact);
            }
        });
    }

    @Transactional
    public String recordClick(String trackingId, String url) {
        emailQueueRepository.findByTrackingId(trackingId).ifPresent(queue -> {
            boolean firstClick = !emailTrackingRepository.existsByTrackingIdAndEventType(
                trackingId, EmailTracking.TrackingEvent.CLICKED);

            EmailTracking tracking = new EmailTracking();
            tracking.setTrackingId(trackingId + "_click_" + UUID.randomUUID());
            tracking.setCampaign(queue.getCampaign());
            tracking.setContact(queue.getContact());
            tracking.setEmailQueue(queue);
            tracking.setEventType(EmailTracking.TrackingEvent.CLICKED);
            tracking.setOriginalUrl(url);
            tracking.setFirstClick(firstClick);
            emailTrackingRepository.save(tracking);

            if (firstClick) {
                updateCampaignStats(queue.getCampaign(), "click");
                Contact contact = queue.getContact();
                contact.setLastClickedAt(LocalDateTime.now());
                contact.setEngagementScore(contact.getEngagementScore() + 10);
                contactRepository.save(contact);
            }
        });
        return url;
    }

}
