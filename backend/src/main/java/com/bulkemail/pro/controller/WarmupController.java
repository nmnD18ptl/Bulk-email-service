package com.bulkemail.pro.controller;

import com.bulkemail.pro.model.entity.WarmupPlan;
import com.bulkemail.pro.repository.WarmupPlanRepository;
import com.bulkemail.pro.security.TenantContext;
import com.bulkemail.pro.service.WarmupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/warmup")
@RequiredArgsConstructor
public class WarmupController {

    private final WarmupPlanRepository warmupPlanRepository;
    private final WarmupService warmupService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<List<WarmupPlan>> list() {
        Long orgId = TenantContext.getOrganizationId();
        return ResponseEntity.ok(orgId != null
            ? warmupPlanRepository.findByOrganizationId(orgId)
            : warmupPlanRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<WarmupPlan> getById(@PathVariable Long id) {
        Long orgId = TenantContext.getOrganizationId();
        return warmupPlanRepository.findById(id)
            .filter(w -> orgId == null || orgId.equals(w.getOrganizationId()))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<WarmupPlan> create(@RequestBody WarmupPlan plan) {
        Long orgId = TenantContext.getOrganizationId();
        plan.setOrganizationId(orgId);
        return ResponseEntity.ok(warmupService.createPlan(plan));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<WarmupPlan> start(@PathVariable Long id) {
        return ResponseEntity.ok(warmupService.startPlan(id));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<WarmupPlan> pause(@PathVariable Long id) {
        return ResponseEntity.ok(warmupService.pausePlan(id));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<WarmupPlan> resume(@PathVariable Long id) {
        return ResponseEntity.ok(warmupService.resumePlan(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long orgId = TenantContext.getOrganizationId();
        return warmupPlanRepository.findById(id)
            .filter(w -> orgId == null || orgId.equals(w.getOrganizationId()))
            .map(w -> {
                warmupPlanRepository.deleteById(id);
                return ResponseEntity.noContent().<Void>build();
            }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/schedule/{targetVolume}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> generateSchedule(
            @PathVariable int targetVolume) {
        return ResponseEntity.ok(warmupService.generateSchedule(targetVolume));
    }
}
