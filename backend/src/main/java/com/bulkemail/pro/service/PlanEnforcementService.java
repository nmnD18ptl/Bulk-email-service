package com.bulkemail.pro.service;

import com.bulkemail.pro.model.entity.Organization;
import com.bulkemail.pro.repository.ContactRepository;
import com.bulkemail.pro.repository.OrganizationRepository;
import com.bulkemail.pro.repository.SmtpConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanEnforcementService {

    private final OrganizationRepository organizationRepository;
    private final ContactRepository contactRepository;
    private final SmtpConfigRepository smtpConfigRepository;

    public void checkCanSendCampaign(Long orgId, int recipientCount) {
        if (orgId == null) return; // single-user mode (no org yet)
        Organization org = organizationRepository.findById(orgId).orElse(null);
        if (org == null) return;

        if (!org.isActive()) {
            throw new PlanLimitException("Organization is inactive.");
        }

        int limit = org.getMonthlyEmailLimit();
        if (limit == Integer.MAX_VALUE) return; // enterprise unlimited

        int remaining = limit - org.getEmailsSentThisMonth();
        if (recipientCount > remaining) {
            throw new PlanLimitException(
                "Monthly email limit reached. " + remaining + " emails remaining this month. "
                + "Upgrade your plan to send more.");
        }
    }

    public void checkCanImportContacts(Long orgId, int newContactCount) {
        if (orgId == null) return;
        Organization org = organizationRepository.findById(orgId).orElse(null);
        if (org == null) return;

        int limit = org.getMaxContacts();
        if (limit == Integer.MAX_VALUE) return;

        long current = contactRepository.countByOrganizationId(orgId);
        if (current + newContactCount > limit) {
            throw new PlanLimitException(
                "Contact limit reached (" + limit + "). "
                + "Currently have " + current + ". Upgrade to add more.");
        }
    }

    public void checkCanAddSmtpConfig(Long orgId) {
        if (orgId == null) return;
        Organization org = organizationRepository.findById(orgId).orElse(null);
        if (org == null) return;

        long current = smtpConfigRepository.countByOrganizationId(orgId);
        if (current >= org.getMaxSmtpConfigs()) {
            throw new PlanLimitException(
                "SMTP config limit reached (" + org.getMaxSmtpConfigs() + "). "
                + "Upgrade your plan to add more.");
        }
    }

    @Transactional
    public void incrementEmailsSent(Long orgId, int count) {
        if (orgId == null) return;
        organizationRepository.findById(orgId).ifPresent(org -> {
            org.setEmailsSentThisMonth(org.getEmailsSentThisMonth() + count);
            organizationRepository.save(org);
        });
    }

    public static class PlanLimitException extends RuntimeException {
        public PlanLimitException(String message) { super(message); }
    }
}
