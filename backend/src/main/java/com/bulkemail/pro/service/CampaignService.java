package com.bulkemail.pro.service;

import com.bulkemail.pro.model.entity.*;
import com.bulkemail.pro.repository.*;
import com.bulkemail.pro.security.TenantContext;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CampaignService {

    private final CampaignRepository    campaignRepository;
    private final ContactRepository      contactRepository;
    private final EmailQueueRepository   emailQueueRepository;
    private final SmtpConfigService      smtpConfigService;
    private final BatchProcessor         batchProcessor;
    private final EmailSenderService     emailSenderService;
    private final PlanEnforcementService planEnforcementService;
    private final OutboxEventRepository  outboxEventRepository;

    public Page<Campaign> findAll(Pageable pageable) {
        Long orgId = TenantContext.getOrganizationId();
        if (orgId != null) {
            return campaignRepository.findByOrganizationId(orgId, pageable);
        }
        return campaignRepository.findAll(pageable);
    }

    public Optional<Campaign> findById(Long id) {
        Long orgId = TenantContext.getOrganizationId();
        return campaignRepository.findById(id)
            .filter(c -> orgId == null || orgId.equals(c.getOrganizationId()));
    }

    public Campaign save(Campaign campaign) {
        Long orgId = TenantContext.getOrganizationId();
        if (orgId != null) {
            campaign.setOrganizationId(orgId);
        }
        return campaignRepository.save(campaign);
    }

    public void delete(Long id) {
        findById(id).ifPresent(c -> campaignRepository.deleteById(id));
    }

    public Campaign sendCampaign(Long campaignId) {
        Campaign campaign = findById(campaignId)
            .orElseThrow(() -> new RuntimeException("Campaign not found"));

        if (campaign.getStatus() == Campaign.CampaignStatus.SENDING) {
            throw new RuntimeException("Campaign is already sending");
        }

        List<Contact> recipients = gatherRecipients(campaign);
        if (recipients.isEmpty()) {
            throw new RuntimeException("No active contacts found for this campaign");
        }

        planEnforcementService.checkCanSendCampaign(TenantContext.getOrganizationId(), recipients.size());

        batchProcessor.buildQueue(campaign, recipients);

        // Write outbox event in same transaction — relay publishes to RabbitMQ
        // so CampaignTriggerConsumer picks it up and acquires the distributed lock
        OutboxEvent trigger = new OutboxEvent(
                "CAMPAIGN", campaignId, "CAMPAIGN_SEND_REQUESTED",
                Map.of("campaignId", campaignId,
                       "organizationId", campaign.getOrganizationId() != null ? campaign.getOrganizationId() : 0L),
                campaign.getOrganizationId());
        outboxEventRepository.save(trigger);

        return campaignRepository.findById(campaignId).orElse(campaign);
    }

    private List<Contact> gatherRecipients(Campaign campaign) {
        Long orgId = TenantContext.getOrganizationId();
        if (orgId != null) {
            if (!campaign.getTagFilters().isEmpty()) {
                Set<Contact> contacts = new HashSet<>();
                for (Long tagId : campaign.getTagFilters()) {
                    contacts.addAll(contactRepository.findByTagIdAndActiveAndOrganizationId(tagId, orgId));
                }
                return new ArrayList<>(contacts);
            }
            return contactRepository.findAllActiveByOrganizationId(orgId);
        }

        // Fallback: no tenant context (e.g. internal/scheduled calls)
        if (!campaign.getTagFilters().isEmpty()) {
            Set<Contact> contacts = new HashSet<>();
            for (Long tagId : campaign.getTagFilters()) {
                contacts.addAll(contactRepository.findByTagIdAndActiveAndOrganizationId(tagId, campaign.getOrganizationId() != null ? campaign.getOrganizationId() : -1));
            }
            return new ArrayList<>(contacts);
        }
        return campaign.getOrganizationId() != null
            ? contactRepository.findAllActiveByOrganizationId(campaign.getOrganizationId())
            : contactRepository.findAllActive();
    }

    public Campaign pauseCampaign(Long campaignId) {
        findById(campaignId).orElseThrow(() -> new RuntimeException("Campaign not found"));
        batchProcessor.pauseCampaign(campaignId);
        return campaignRepository.findById(campaignId).orElseThrow();
    }

    public Campaign resumeCampaign(Long campaignId) {
        findById(campaignId).orElseThrow(() -> new RuntimeException("Campaign not found"));
        batchProcessor.resumeCampaign(campaignId);
        return campaignRepository.findById(campaignId).orElseThrow();
    }

    public Campaign cancelCampaign(Long campaignId) {
        findById(campaignId).orElseThrow(() -> new RuntimeException("Campaign not found"));
        batchProcessor.cancelCampaign(campaignId);
        return campaignRepository.findById(campaignId).orElseThrow();
    }

    public Map<String, Object> getCampaignStats(Long campaignId) {
        Campaign campaign = findById(campaignId)
            .orElseThrow(() -> new RuntimeException("Campaign not found"));

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRecipients", campaign.getTotalRecipients());
        stats.put("sentCount", campaign.getSentCount());
        stats.put("deliveredCount", campaign.getDeliveredCount());
        stats.put("openCount", campaign.getOpenCount());
        stats.put("clickCount", campaign.getClickCount());
        stats.put("bounceCount", campaign.getBounceCount());
        stats.put("unsubscribeCount", campaign.getUnsubscribeCount());
        stats.put("complaintCount", campaign.getComplaintCount());
        stats.put("failedCount", campaign.getFailedCount());
        stats.put("openRate", campaign.getOpenRate());
        stats.put("clickRate", campaign.getClickRate());
        stats.put("bounceRate", campaign.getBounceRate());
        stats.put("status", campaign.getStatus());

        long pending = emailQueueRepository.countByCampaignIdAndStatus(
            campaignId, EmailQueue.QueueStatus.PENDING);
        stats.put("pendingCount", pending);

        return stats;
    }

    public List<Campaign> getRecentCampaigns(int limit) {
        Long orgId = TenantContext.getOrganizationId();
        Pageable pageable = PageRequest.of(0, limit);
        if (orgId != null) {
            return campaignRepository.findRecentByOrganizationId(orgId, pageable);
        }
        return campaignRepository.findRecentByOrganizationId(-1L, pageable);
    }

    public Map<String, Object> getDashboardStats() {
        Long orgId = TenantContext.getOrganizationId();
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalCampaigns", orgId != null ? campaignRepository.countByOrganizationId(orgId) : campaignRepository.count());
        stats.put("totalContacts", orgId != null ? contactRepository.countByOrganizationId(orgId) : contactRepository.count());

        Object[] overall = orgId != null
            ? (Object[]) campaignRepository.getOverallStatsByOrganizationId(orgId)
            : null;

        if (overall != null && overall.length >= 4) {
            stats.put("totalSent", overall[0] != null ? overall[0] : 0);
            stats.put("totalOpens", overall[1] != null ? overall[1] : 0);
            stats.put("totalClicks", overall[2] != null ? overall[2] : 0);
            stats.put("totalBounces", overall[3] != null ? overall[3] : 0);
        } else {
            stats.put("totalSent", 0);
            stats.put("totalOpens", 0);
            stats.put("totalClicks", 0);
            stats.put("totalBounces", 0);
        }

        stats.put("recentCampaigns", getRecentCampaigns(5));
        return stats;
    }

    public void sendTestEmail(Long campaignId, String testEmail) throws Exception {
        Campaign campaign = findById(campaignId)
            .orElseThrow(() -> new RuntimeException("Campaign not found"));

        if (campaign.getHtmlContent() == null || campaign.getHtmlContent().isBlank()) {
            throw new RuntimeException("Campaign has no email body. Please add HTML content in the campaign editor before sending a test.");
        }
        if (campaign.getSubject() == null || campaign.getSubject().isBlank()) {
            throw new RuntimeException("Campaign has no subject line. Please set a subject before sending a test.");
        }

        SmtpConfig smtpConfig = campaign.getSmtpConfig() != null
            ? campaign.getSmtpConfig()
            : smtpConfigService.getDefault()
                .orElseThrow(() -> new RuntimeException("No SMTP server configured. Please add an SMTP configuration in Settings first."));

        JavaMailSenderImpl mailSender = smtpConfigService.buildMailSender(smtpConfig);
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        String fromEmail = campaign.getFromEmail() != null ? campaign.getFromEmail() : smtpConfig.getFromEmail();
        String fromName  = campaign.getFromName()  != null ? campaign.getFromName()  : smtpConfig.getFromName();

        helper.setFrom(fromEmail, fromName != null ? fromName : fromEmail);
        helper.setTo(testEmail);
        helper.setSubject("[TEST] " + campaign.getSubject());

        String html = campaign.getHtmlContent()
            .replace("{{FirstName}}", "Test")
            .replace("{{LastName}}", "User")
            .replace("{{Email}}", testEmail)
            .replace("{{UnsubscribeLink}}", "#test-unsubscribe");

        if (campaign.getTextContent() != null && !campaign.getTextContent().isBlank()) {
            helper.setText(campaign.getTextContent(), html);
        } else {
            helper.setText(html, true);
        }

        mailSender.send(message);
    }
}
