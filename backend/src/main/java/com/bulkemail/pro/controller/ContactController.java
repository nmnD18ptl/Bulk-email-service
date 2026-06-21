package com.bulkemail.pro.controller;

import com.bulkemail.pro.model.dto.ImportPreviewResponse;
import com.bulkemail.pro.model.entity.Contact;
import com.bulkemail.pro.service.ContactService;
import com.bulkemail.pro.service.EmailValidationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;
    private final EmailValidationService emailValidationService;
    private final ObjectMapper objectMapper;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<Page<Contact>> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String dir) {

        PageRequest pageable = PageRequest.of(page, size,
            dir.equalsIgnoreCase("ASC") ? Sort.by(sort).ascending() : Sort.by(sort).descending());
        return ResponseEntity.ok(contactService.findAll(search, status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<Contact> getById(@PathVariable Long id) {
        return contactService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Contact> create(@RequestBody Contact contact) {
        return ResponseEntity.ok(contactService.save(contact));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Contact> update(@PathVariable Long id, @RequestBody Contact contact) {
        return contactService.findById(id).map(existing -> {
            contact.setId(id);
            return ResponseEntity.ok(contactService.save(contact));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contactService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Step 1 of the guided import flow.
     * Parses the file and returns its column headers plus up to 5 sample rows.
     * Nothing is saved — the frontend uses this to render the column-mapping UI.
     */
    @PostMapping("/import/preview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<ImportPreviewResponse> previewImport(
            @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(contactService.previewImport(file));
    }

    /**
     * Imports contacts from a CSV or Excel file.
     *
     * When {@code columnMapping} is present (sent as a JSON string by the
     * column-mapping UI), the explicit mapping is used and header auto-detection
     * is skipped entirely.  When absent, the original auto-detection path runs
     * unchanged — fully backward compatible.
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Map<String, Object>> importContacts(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "columnMapping", required = false) String columnMappingJson)
            throws Exception {

        if (columnMappingJson != null && !columnMappingJson.isBlank()) {
            Map<String, Integer> mapping = objectMapper.readValue(
                    columnMappingJson, new TypeReference<Map<String, Integer>>() {});
            return ResponseEntity.ok(contactService.importWithMapping(file, tags, mapping));
        }
        return ResponseEntity.ok(contactService.importFromFile(file, tags));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<byte[]> exportContacts(
            @RequestParam(defaultValue = "") String status) throws Exception {
        byte[] csv = contactService.exportToCsv(status.isEmpty() ? null : status);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contacts.csv\"")
            .contentType(MediaType.TEXT_PLAIN)
            .body(csv);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(contactService.getStats());
    }

    @PostMapping("/validate-email")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<EmailValidationService.ValidationResult> validateEmail(
            @RequestBody Map<String, String> body) {
        String email = body.get("email");
        return ResponseEntity.ok(emailValidationService.validate(email));
    }
}
