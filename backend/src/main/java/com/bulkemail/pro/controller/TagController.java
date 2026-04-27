package com.bulkemail.pro.controller;

import com.bulkemail.pro.model.entity.Tag;
import com.bulkemail.pro.repository.TagRepository;
import com.bulkemail.pro.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagRepository tagRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<List<Map<String, Object>>> list() {
        Long orgId = TenantContext.getOrganizationId();
        List<Object[]> results = orgId != null
            ? tagRepository.findAllWithContactCountByOrganizationId(orgId)
            : tagRepository.findAllWithContactCountByOrganizationId(-1L);
        List<Map<String, Object>> response = new ArrayList<>();
        for (Object[] row : results) {
            Tag tag = (Tag) row[0];
            Long count = (Long) row[1];
            Map<String, Object> item = new HashMap<>();
            item.put("id", tag.getId());
            item.put("name", tag.getName());
            item.put("color", tag.getColor());
            item.put("description", tag.getDescription());
            item.put("contactCount", count);
            response.add(item);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Tag> create(@RequestBody Tag tag) {
        Long orgId = TenantContext.getOrganizationId();
        if (orgId != null && tagRepository.existsByNameAndOrganizationId(tag.getName(), orgId)) {
            return ResponseEntity.badRequest().build();
        }
        tag.setOrganizationId(orgId);
        return ResponseEntity.ok(tagRepository.save(tag));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Tag> update(@PathVariable Long id, @RequestBody Tag tag) {
        Long orgId = TenantContext.getOrganizationId();
        return tagRepository.findById(id)
            .filter(t -> orgId == null || orgId.equals(t.getOrganizationId()))
            .map(existing -> {
                existing.setName(tag.getName());
                existing.setColor(tag.getColor());
                existing.setDescription(tag.getDescription());
                return ResponseEntity.ok(tagRepository.save(existing));
            }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long orgId = TenantContext.getOrganizationId();
        return tagRepository.findById(id)
            .filter(t -> orgId == null || orgId.equals(t.getOrganizationId()))
            .map(t -> {
                tagRepository.deleteById(id);
                return ResponseEntity.noContent().<Void>build();
            }).orElse(ResponseEntity.notFound().build());
    }
}
