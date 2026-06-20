package com.bulkemail.pro.controller;

import com.bulkemail.pro.model.entity.Template;
import com.bulkemail.pro.repository.TemplateRepository;
import com.bulkemail.pro.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateRepository templateRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<List<Template>> list(
            @RequestParam(required = false) String category) {
        Long orgId = TenantContext.getOrganizationId();
        if (category != null && !category.isEmpty()) {
            return ResponseEntity.ok(orgId != null
                ? templateRepository.findByOrganizationIdOrBuiltInAndCategory(orgId, category)
                : templateRepository.findByCategory(category));
        }
        return ResponseEntity.ok(orgId != null
            ? templateRepository.findByOrganizationIdOrBuiltIn(orgId)
            : templateRepository.findByIsActive(true));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<Template> getById(@PathVariable Long id) {
        Long orgId = TenantContext.getOrganizationId();
        return templateRepository.findById(id)
            .filter(t -> t.isBuiltIn() || orgId == null || orgId.equals(t.getOrganizationId()))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Template> create(@RequestBody Template template) {
        Long orgId = TenantContext.getOrganizationId();
        template.setBuiltIn(false);
        template.setOrganizationId(orgId);
        return ResponseEntity.ok(templateRepository.save(template));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Template> update(@PathVariable Long id, @RequestBody Template template) {
        Long orgId = TenantContext.getOrganizationId();
        return templateRepository.findById(id)
            .filter(t -> !t.isBuiltIn() && (orgId == null || orgId.equals(t.getOrganizationId())))
            .map(existing -> {
                template.setId(id);
                template.setBuiltIn(false);
                template.setOrganizationId(orgId);
                return ResponseEntity.ok(templateRepository.save(template));
            }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long orgId = TenantContext.getOrganizationId();
        return templateRepository.findById(id)
            .filter(t -> !t.isBuiltIn() && (orgId == null || orgId.equals(t.getOrganizationId())))
            .map(t -> {
                templateRepository.deleteById(id);
                return ResponseEntity.noContent().<Void>build();
            }).orElse(ResponseEntity.notFound().build());
    }
}
