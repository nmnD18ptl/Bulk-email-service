package com.bulkemail.pro.controller;

import com.bulkemail.pro.model.entity.Campaign;
import com.bulkemail.pro.service.CampaignService;
import com.bulkemail.pro.service.SpamScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;
    private final SpamScoreService spamScoreService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<Page<Campaign>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(campaignService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<Campaign> getById(@PathVariable Long id) {
        return campaignService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Campaign> create(@RequestBody Campaign campaign) {
        return ResponseEntity.ok(campaignService.save(campaign));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Campaign> update(@PathVariable Long id, @RequestBody Campaign campaign) {
        return campaignService.findById(id).map(existing -> {
            campaign.setId(id);
            return ResponseEntity.ok(campaignService.save(campaign));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        campaignService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<?> send(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(campaignService.sendCampaign(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Campaign> pause(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.pauseCampaign(id));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Campaign> resume(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.resumeCampaign(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Campaign> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.cancelCampaign(id));
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaignStats(id));
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Map<String, String>> sendTest(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            campaignService.sendTestEmail(id, body.get("email"));
            return ResponseEntity.ok(Map.of("message", "Test email sent successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/analyze-spam")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<SpamScoreService.SpamAnalysisResult> analyzeSpam(
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(spamScoreService.analyze(
            body.get("subject"), body.get("html"), body.get("sender")));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(campaignService.getDashboardStats());
    }
}
