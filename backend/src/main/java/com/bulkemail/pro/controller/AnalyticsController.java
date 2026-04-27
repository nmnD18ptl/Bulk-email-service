package com.bulkemail.pro.controller;

import com.bulkemail.pro.model.entity.Campaign;
import com.bulkemail.pro.model.entity.Contact;
import com.bulkemail.pro.model.entity.EmailQueue;
import com.bulkemail.pro.model.entity.EmailTracking;
import com.bulkemail.pro.repository.CampaignRepository;
import com.bulkemail.pro.repository.ContactRepository;
import com.bulkemail.pro.repository.EmailQueueRepository;
import com.bulkemail.pro.repository.EmailTrackingRepository;
import com.bulkemail.pro.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final CampaignRepository campaignRepository;
    private final ContactRepository contactRepository;
    private final EmailTrackingRepository emailTrackingRepository;
    private final EmailQueueRepository emailQueueRepository;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Long orgId = TenantContext.getOrganizationId();
        Map<String, Object> data = new HashMap<>();

        long totalContacts = orgId != null
            ? contactRepository.countByOrganizationId(orgId)
            : contactRepository.count();
        long activeContacts = orgId != null
            ? contactRepository.countByStatusAndOrganizationId(Contact.ContactStatus.ACTIVE, orgId)
            : contactRepository.countByStatus(Contact.ContactStatus.ACTIVE);
        long totalCampaigns = orgId != null
            ? campaignRepository.countByOrganizationId(orgId)
            : campaignRepository.count();

        data.put("totalContacts", totalContacts);
        data.put("activeContacts", activeContacts);
        data.put("totalCampaigns", totalCampaigns);

        Object[] overall = orgId != null
            ? (Object[]) campaignRepository.getOverallStatsByOrganizationId(orgId)
            : null;

        if (overall != null && overall.length >= 4) {
            long sent = overall[0] != null ? ((Number) overall[0]).longValue() : 0;
            long opens = overall[1] != null ? ((Number) overall[1]).longValue() : 0;
            long clicks = overall[2] != null ? ((Number) overall[2]).longValue() : 0;
            long bounces = overall[3] != null ? ((Number) overall[3]).longValue() : 0;

            data.put("totalSent", sent);
            data.put("totalOpens", opens);
            data.put("totalClicks", clicks);
            data.put("totalBounces", bounces);
            data.put("avgOpenRate", sent > 0 ? (double) opens / sent * 100 : 0);
            data.put("avgClickRate", sent > 0 ? (double) clicks / sent * 100 : 0);
            data.put("avgBounceRate", sent > 0 ? (double) bounces / sent * 100 : 0);
        } else {
            data.put("totalSent", 0);
            data.put("totalOpens", 0);
            data.put("totalClicks", 0);
            data.put("totalBounces", 0);
            data.put("avgOpenRate", 0);
            data.put("avgClickRate", 0);
            data.put("avgBounceRate", 0);
        }

        List<Campaign> recent = orgId != null
            ? campaignRepository.findRecentByOrganizationId(orgId, PageRequest.of(0, 10))
            : List.of();
        data.put("recentCampaigns", recent.stream().map(c -> {
            Map<String, Object> cm = new HashMap<>();
            cm.put("id", c.getId());
            cm.put("name", c.getName());
            cm.put("status", c.getStatus());
            cm.put("sentCount", c.getSentCount());
            cm.put("openRate", c.getOpenRate());
            cm.put("clickRate", c.getClickRate());
            cm.put("createdAt", c.getCreatedAt());
            return cm;
        }).toList());

        return ResponseEntity.ok(data);
    }

    @GetMapping("/campaign/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<Map<String, Object>> getCampaignAnalytics(@PathVariable Long id) {
        Long orgId = TenantContext.getOrganizationId();
        return campaignRepository.findById(id)
            .filter(c -> orgId == null || orgId.equals(c.getOrganizationId()))
            .map(campaign -> {
                Map<String, Object> data = new HashMap<>();
                data.put("campaign", campaign);
                data.put("openRate", campaign.getOpenRate());
                data.put("clickRate", campaign.getClickRate());
                data.put("bounceRate", campaign.getBounceRate());

                List<Object[]> topLinks = emailTrackingRepository.findTopClickedLinks(id);
                data.put("topLinks", topLinks.stream().map(row ->
                    Map.of("url", row[0], "count", row[1])
                ).toList());

                return ResponseEntity.ok(data);
            }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/export/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<byte[]> exportCampaignReport(@PathVariable Long id) {
        Long orgId = TenantContext.getOrganizationId();
        return campaignRepository.findById(id)
            .filter(c -> orgId == null || orgId.equals(c.getOrganizationId()))
            .map(campaign -> {
                StringBuilder csv = new StringBuilder();
                csv.append("Metric,Value\n");
                csv.append("Campaign Name,").append(campaign.getName()).append("\n");
                csv.append("Status,").append(campaign.getStatus()).append("\n");
                csv.append("Total Recipients,").append(campaign.getTotalRecipients()).append("\n");
                csv.append("Sent,").append(campaign.getSentCount()).append("\n");
                csv.append("Opens,").append(campaign.getOpenCount()).append("\n");
                csv.append("Clicks,").append(campaign.getClickCount()).append("\n");
                csv.append("Bounces,").append(campaign.getBounceCount()).append("\n");
                csv.append("Unsubscribes,").append(campaign.getUnsubscribeCount()).append("\n");
                csv.append("Open Rate,").append(String.format("%.2f%%", campaign.getOpenRate())).append("\n");
                csv.append("Click Rate,").append(String.format("%.2f%%", campaign.getClickRate())).append("\n");
                csv.append("Bounce Rate,").append(String.format("%.2f%%", campaign.getBounceRate())).append("\n");

                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"campaign-" + id + "-report.csv\"")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(csv.toString().getBytes());
            }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/export/{id}/recipients")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<byte[]> exportPerRecipient(@PathVariable Long id) {
        Long orgId = TenantContext.getOrganizationId();
        return campaignRepository.findById(id)
            .filter(c -> orgId == null || orgId.equals(c.getOrganizationId()))
            .map(campaign -> {
                List<EmailQueue> all = new ArrayList<>();
                for (EmailQueue.QueueStatus status : EmailQueue.QueueStatus.values()) {
                    all.addAll(emailQueueRepository.findByCampaignIdAndStatus(id, status));
                }

                Set<String> opened = new HashSet<>();
                Set<String> clicked = new HashSet<>();
                emailTrackingRepository.findByCampaignIdAndEventType(id, EmailTracking.TrackingEvent.OPENED)
                    .forEach(t -> opened.add(t.getTrackingId().replaceAll("_open_.*", "")));
                emailTrackingRepository.findByCampaignIdAndEventType(id, EmailTracking.TrackingEvent.CLICKED)
                    .forEach(t -> clicked.add(t.getTrackingId().replaceAll("_click_.*", "")));

                StringBuilder csv = new StringBuilder();
                csv.append("Email,Name,Status,SentAt,ErrorMessage,Opened,Clicked\n");
                for (EmailQueue item : all) {
                    csv.append(csvEscape(item.getRecipientEmail())).append(",")
                       .append(csvEscape(item.getRecipientName())).append(",")
                       .append(item.getStatus()).append(",")
                       .append(item.getSentAt() != null ? item.getSentAt().toString() : "").append(",")
                       .append(csvEscape(item.getErrorMessage())).append(",")
                       .append(opened.contains(item.getTrackingId()) ? "Yes" : "No").append(",")
                       .append(clicked.contains(item.getTrackingId()) ? "Yes" : "No").append("\n");
                }

                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"campaign-" + id + "-recipients.csv\"")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(csv.toString().getBytes());
            }).orElse(ResponseEntity.notFound().build());
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
