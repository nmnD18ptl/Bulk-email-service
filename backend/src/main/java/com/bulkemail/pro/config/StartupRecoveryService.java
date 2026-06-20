package com.bulkemail.pro.config;

import com.bulkemail.pro.model.entity.Campaign;
import com.bulkemail.pro.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * On application startup, finds any campaigns stuck in SENDING state (from a previous crash/restart)
 * and moves them to PAUSED so they can be manually resumed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartupRecoveryService {

    private final CampaignRepository campaignRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverStuckCampaigns() {
        List<Campaign> stuckCampaigns = campaignRepository.findByStatusOrderByCreatedAtDesc(Campaign.CampaignStatus.SENDING);
        if (stuckCampaigns.isEmpty()) return;

        log.warn("Found {} campaign(s) stuck in SENDING state — setting to PAUSED for manual resume.",
            stuckCampaigns.size());

        for (Campaign campaign : stuckCampaigns) {
            campaign.setStatus(Campaign.CampaignStatus.PAUSED);
            campaignRepository.save(campaign);
            log.warn("Recovered stuck campaign id={} name='{}' → PAUSED", campaign.getId(), campaign.getName());
        }
    }
}
