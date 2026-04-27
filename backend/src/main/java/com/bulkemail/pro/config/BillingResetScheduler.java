package com.bulkemail.pro.config;

import com.bulkemail.pro.model.entity.Organization;
import com.bulkemail.pro.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillingResetScheduler {

    private final OrganizationRepository organizationRepository;

    /**
     * Runs daily at midnight. Resets emailsSentThisMonth for any org whose billing cycle
     * started on this day-of-month (i.e., their monthly anniversary).
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void resetMonthlyEmailCounters() {
        int todayDom = LocalDate.now().getDayOfMonth();
        List<Organization> orgs = organizationRepository.findAll();

        int resetCount = 0;
        for (Organization org : orgs) {
            if (!org.isActive()) continue;
            LocalDate cycleStart = org.getBillingCycleStart();
            if (cycleStart != null && cycleStart.getDayOfMonth() == todayDom) {
                org.setEmailsSentThisMonth(0);
                organizationRepository.save(org);
                resetCount++;
                log.info("Reset monthly email counter for org {} ({})", org.getId(), org.getName());
            }
        }
        if (resetCount > 0) {
            log.info("Monthly billing reset complete: {} organization(s) reset", resetCount);
        }
    }
}
