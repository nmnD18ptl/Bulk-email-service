package com.bulkemail.pro.service;

import com.bulkemail.pro.model.dto.ImportPreviewResponse;
import com.bulkemail.pro.model.entity.Contact;
import com.bulkemail.pro.model.entity.SuppressionList;
import com.bulkemail.pro.model.entity.Tag;
import com.bulkemail.pro.repository.ContactRepository;
import com.bulkemail.pro.repository.SuppressionListRepository;
import com.bulkemail.pro.repository.TagRepository;
import com.bulkemail.pro.security.TenantContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContactService {

    private final ContactRepository contactRepository;
    private final TagRepository tagRepository;
    private final EmailValidationService emailValidationService;
    private final SuppressionListRepository suppressionListRepository;

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    private static final String[] FIELD_PREFIXES = {
        "Email :", "Email:", "E-Mail :", "E-Mail:",
        "Tel :", "Tel:", "Phone :", "Phone:", "Mob :", "Mob:", "Mobile :", "Mobile:",
        "PIN :", "PIN:", "Pincode :", "Pincode:", "Pin :", "Pin:",
        "Fax :", "Fax:"
    };

    public Page<Contact> findAll(String search, String status, Pageable pageable) {
        Long orgId = TenantContext.getOrganizationId();
        Contact.ContactStatus contactStatus = null;
        if (status != null && !status.isEmpty()) {
            try { contactStatus = Contact.ContactStatus.valueOf(status.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }
        String searchParam = (search == null || search.isEmpty()) ? null : search;
        if (orgId != null) {
            return contactRepository.findByOrganizationIdAndSearchAndStatus(orgId, searchParam, contactStatus, pageable);
        }
        return contactRepository.findByOrganizationIdAndSearchAndStatus(-1L, searchParam, contactStatus, pageable);
    }

    public Optional<Contact> findById(Long id) {
        Long orgId = TenantContext.getOrganizationId();
        return contactRepository.findById(id)
            .filter(c -> orgId == null || orgId.equals(c.getOrganizationId()));
    }

    @CacheEvict(value = "contact-stats", key = "T(com.bulkemail.pro.security.TenantContext).getOrganizationId()")
    public Contact save(Contact contact) {
        Long orgId = TenantContext.getOrganizationId();
        if (orgId != null) {
            contact.setOrganizationId(orgId);
        }
        if (contact.getUnsubscribeToken() == null) {
            contact.setUnsubscribeToken(UUID.randomUUID().toString());
        }
        return contactRepository.save(contact);
    }

    @CacheEvict(value = "contact-stats", key = "T(com.bulkemail.pro.security.TenantContext).getOrganizationId()")
    public void delete(Long id) {
        findById(id).ifPresent(c -> contactRepository.deleteById(id));
    }

    public Map<String, Object> importFromFile(MultipartFile file, List<String> tagNames) throws Exception {
        String filename = file.getOriginalFilename() != null
            ? file.getOriginalFilename().toLowerCase() : "";

        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls") && !filename.endsWith(".csv")) {
            throw new IllegalArgumentException("Unsupported file type. Please upload a .xlsx, .xls, or .csv file.");
        }
        if (filename.endsWith(".xls")) {
            throw new IllegalArgumentException("Old Excel format (.xls) is not supported. Please save the file as .xlsx and re-upload.");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty.");
        }

        List<Map<String, String>> rows;
        if (filename.endsWith(".xlsx")) {
            rows = parseExcel(file);
        } else {
            rows = parseCsv(file);
        }

        return processImportedRows(rows, tagNames);
    }

    private List<Map<String, String>> parseExcel(MultipartFile file) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 0) return rows;

            Row firstRow = sheet.getRow(0);
            if (firstRow == null) return rows;

            boolean hasHeader = detectHeaderRow(firstRow);

            if (hasHeader) {
                List<String> headers = new ArrayList<>();
                for (Cell cell : firstRow) {
                    headers.add(cleanValue(cell.getStringCellValue()).toLowerCase());
                }
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    Map<String, String> rowData = new LinkedHashMap<>();
                    for (int j = 0; j < headers.size(); j++) {
                        rowData.put(headers.get(j), cleanValue(getCellValue(row.getCell(j))));
                    }
                    rows.add(rowData);
                }
            } else {
                // APEDA / positional format: col B=Company, C=Address, D=State, E=PIN, F=Tel, G=Email
                for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    Map<String, String> rowData = new LinkedHashMap<>();
                    rowData.put("company", cleanValue(getCellValue(row.getCell(1))));
                    rowData.put("address", cleanValue(getCellValue(row.getCell(2))));
                    rowData.put("state",   cleanValue(getCellValue(row.getCell(3))));
                    rowData.put("pin",     cleanValue(getCellValue(row.getCell(4))));
                    rowData.put("phone",   cleanValue(getCellValue(row.getCell(5))));
                    rowData.put("email",   cleanValue(getCellValue(row.getCell(6))));
                    rows.add(rowData);
                }
            }
        }
        return rows;
    }

    private boolean detectHeaderRow(Row row) {
        Set<String> headerKeywords = Set.of(
            "email", "e-mail", "emailaddress", "name", "firstname", "first_name",
            "lastname", "last_name", "company", "phone", "mobile", "country", "state");
        for (Cell cell : row) {
            String val = getCellValue(cell).toLowerCase().trim();
            if (headerKeywords.contains(val)) return true;
        }
        return false;
    }

    private List<Map<String, String>> parseCsv(MultipartFile file) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return rows;

            String[] headers = splitCsvLine(headerLine);
            for (int i = 0; i < headers.length; i++) {
                headers[i] = cleanValue(headers[i]).toLowerCase();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = splitCsvLine(line);
                Map<String, String> rowData = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String val = i < values.length ? cleanValue(values[i]) : "";
                    rowData.put(headers[i], val);
                }
                rows.add(rowData);
            }
        }
        return rows;
    }

    private String[] splitCsvLine(String line) {
        String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim().replaceAll("^\"|\"$", "");
        }
        return parts;
    }

    private Map<String, Object> processImportedRows(List<Map<String, String>> rows, List<String> tagNames) {
        Long orgId = TenantContext.getOrganizationId();
        int imported = 0, duplicates = 0, invalid = 0, suppressed = 0;
        List<Map<String, String>> rejectedRows = new ArrayList<>();

        Set<Tag> tags = new HashSet<>();
        if (tagNames != null) {
            for (String tagName : tagNames) {
                if (StringUtils.isBlank(tagName)) continue;
                Tag tag;
                if (orgId != null) {
                    tag = tagRepository.findByNameAndOrganizationId(tagName.trim(), orgId)
                        .orElseGet(() -> {
                            Tag t = new Tag(tagName.trim());
                            t.setOrganizationId(orgId);
                            return tagRepository.save(t);
                        });
                } else {
                    tag = tagRepository.findByName(tagName.trim())
                        .orElseGet(() -> tagRepository.save(new Tag(tagName.trim())));
                }
                tags.add(tag);
            }
        }

        // Load all existing emails for this org once (N+1 fix)
        Set<String> existingEmails = orgId != null
            ? contactRepository.findAllEmailsByOrganizationId(orgId)
            : new HashSet<>();

        Set<String> seenInBatch = new HashSet<>();
        List<Contact> toSave = new ArrayList<>();

        for (Map<String, String> row : rows) {
            String email = extractEmail(row);

            if (StringUtils.isBlank(email)) {
                invalid++;
                addRejectedRow(rejectedRows, row, "MISSING_EMAIL");
                continue;
            }

            String normalizedEmail = email.toLowerCase().trim();

            if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
                invalid++;
                addRejectedRow(rejectedRows, row, "INVALID_FORMAT");
                continue;
            }

            if (seenInBatch.contains(normalizedEmail)) {
                duplicates++;
                continue;
            }

            if (existingEmails.contains(normalizedEmail)) {
                // Check suppression status
                Optional<Contact> existing = orgId != null
                    ? contactRepository.findByEmailAndOrganizationId(normalizedEmail, orgId)
                    : contactRepository.findByEmail(normalizedEmail);
                if (existing.isPresent()) {
                    Contact.ContactStatus st = existing.get().getStatus();
                    if (st == Contact.ContactStatus.UNSUBSCRIBED ||
                        st == Contact.ContactStatus.BOUNCED ||
                        st == Contact.ContactStatus.COMPLAINED) {
                        suppressed++;
                        addRejectedRow(rejectedRows, row, "SUPPRESSED_" + st.name());
                        seenInBatch.add(normalizedEmail);
                        continue;
                    }
                }
                duplicates++;
                seenInBatch.add(normalizedEmail);
                continue;
            }

            seenInBatch.add(normalizedEmail);
            existingEmails.add(normalizedEmail);

            Contact contact = new Contact();
            contact.setEmail(normalizedEmail);
            contact.setFirstName(extractField(row, "firstname", "first_name", "first name", "fname"));
            contact.setLastName(extractField(row, "lastname", "last_name", "last name", "lname"));
            contact.setCompany(extractField(row, "company", "organization", "company name"));
            contact.setCountry(extractField(row, "country", "state"));
            contact.setPhone(extractField(row, "phone", "telephone", "mobile", "mob", "tel"));
            contact.setCustomField1(extractField(row, "pin", "customfield1", "custom1", "custom_field_1"));
            contact.setCustomField2(extractField(row, "address", "customfield2", "custom2", "custom_field_2"));
            contact.setCustomField3(extractField(row, "customfield3", "custom3", "custom_field_3"));
            contact.setCustomField4(extractField(row, "customfield4", "custom4", "custom_field_4"));
            contact.setCustomField5(extractField(row, "customfield5", "custom5", "custom_field_5"));
            contact.setUnsubscribeToken(UUID.randomUUID().toString());
            contact.setOrganizationId(orgId);
            contact.setTags(new HashSet<>(tags));

            toSave.add(contact);
            imported++;
        }

        contactRepository.saveAll(toSave);

        Map<String, Object> result = new HashMap<>();
        result.put("imported", imported);
        result.put("duplicates", duplicates);
        result.put("suppressed", suppressed);
        result.put("invalid", invalid);
        result.put("total", rows.size());
        result.put("rejectedRows", rejectedRows);
        return result;
    }

    private void addRejectedRow(List<Map<String, String>> rejected, Map<String, String> row, String reason) {
        Map<String, String> entry = new LinkedHashMap<>(row);
        entry.put("_rejectionReason", reason);
        rejected.add(entry);
    }

    private String cleanValue(String raw) {
        if (raw == null) return "";
        String cleaned = raw
            .replace('\u00a0', ' ')
            .replace('\u200b', ' ')
            .replace('\ufeff', ' ')
            .replace('\u00ad', ' ')
            .trim();
        for (String prefix : FIELD_PREFIXES) {
            if (cleaned.regionMatches(true, 0, prefix, 0, prefix.length())) {
                cleaned = cleaned.substring(prefix.length()).trim();
                break;
            }
        }
        return cleaned;
    }

    private String extractEmail(Map<String, String> row) {
        return extractField(row, "email", "email address", "e-mail", "emailaddress");
    }

    private String extractField(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String val = row.get(key);
            if (StringUtils.isNotBlank(val)) return val.trim();
        }
        return null;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) yield cell.getLocalDateTimeCellValue().toString();
                yield String.valueOf((long) cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.STRING
                ? cell.getStringCellValue()
                : String.valueOf(cell.getNumericCellValue());
            default -> "";
        };
    }

    public byte[] exportToCsv(String status) throws IOException {
        Long orgId = TenantContext.getOrganizationId();
        List<Contact> contacts;
        if (orgId != null) {
            contacts = status != null && !status.isEmpty()
                ? contactRepository.findByOrganizationIdAndSearchAndStatus(
                    orgId, null, Contact.ContactStatus.valueOf(status.toUpperCase()), Pageable.unpaged()).getContent()
                : contactRepository.findAllActiveByOrganizationId(orgId);
        } else {
            contacts = contactRepository.findAll();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Email,FirstName,LastName,Company,Country,Phone,Status,CustomField1,CustomField2,CustomField3,CustomField4,CustomField5\n");
        for (Contact c : contacts) {
            sb.append(csvEscape(c.getEmail())).append(",")
              .append(csvEscape(c.getFirstName())).append(",")
              .append(csvEscape(c.getLastName())).append(",")
              .append(csvEscape(c.getCompany())).append(",")
              .append(csvEscape(c.getCountry())).append(",")
              .append(csvEscape(c.getPhone())).append(",")
              .append(c.getStatus()).append(",")
              .append(csvEscape(c.getCustomField1())).append(",")
              .append(csvEscape(c.getCustomField2())).append(",")
              .append(csvEscape(c.getCustomField3())).append(",")
              .append(csvEscape(c.getCustomField4())).append(",")
              .append(csvEscape(c.getCustomField5())).append("\n");
        }
        return sb.toString().getBytes();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ── Column-mapping import (new) ───────────────────────────────────────────

    /**
     * Parses the uploaded file and returns its column headers plus up to five
     * sample data rows.  Nothing is persisted — this is a read-only preview
     * used to power the column-mapping UI on the frontend.
     */
    public ImportPreviewResponse previewImport(MultipartFile file) throws Exception {
        String filename = resolvedFilename(file);
        validateFilename(filename);
        if (file.isEmpty()) throw new IllegalArgumentException("The uploaded file is empty.");
        return filename.endsWith(".xlsx") ? previewExcel(file) : previewCsv(file);
    }

    /**
     * Imports contacts using an explicit column-index mapping supplied by the
     * user rather than auto-detecting headers.
     *
     * {@code columnMapping} keys are camelCase field names
     * (email, firstName, lastName, company, country, phone, customField1 … customField5).
     * Values are 0-based column indices in the source file.  Null values mean
     * "don't import this field".
     *
     * All existing duplicate-detection, suppression, and tag logic in
     * {@link #processImportedRows} is reused unchanged.
     */
    public Map<String, Object> importWithMapping(MultipartFile file,
                                                 List<String> tagNames,
                                                 Map<String, Integer> columnMapping) throws Exception {
        String filename = resolvedFilename(file);
        validateFilename(filename);
        if (file.isEmpty()) throw new IllegalArgumentException("The uploaded file is empty.");
        if (columnMapping.get("email") == null) {
            throw new IllegalArgumentException("Column mapping must include the email field.");
        }
        List<Map<String, String>> rows = filename.endsWith(".xlsx")
                ? parseExcelWithMapping(file, columnMapping)
                : parseCsvWithMapping(file, columnMapping);
        return processImportedRows(rows, tagNames);
    }

    // ── Private: preview helpers ──────────────────────────────────────────────

    private ImportPreviewResponse previewExcel(MultipartFile file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 0) {
                return new ImportPreviewResponse(List.of(), List.of(), 0);
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return new ImportPreviewResponse(List.of(), List.of(), 0);

            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cleanValue(getCellValue(cell)));
            }

            List<List<String>> sampleRows = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int i = 1; i <= Math.min(5, lastRow); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                List<String> rowData = new ArrayList<>();
                for (int j = 0; j < headers.size(); j++) {
                    rowData.add(cleanValue(getCellValue(row.getCell(j))));
                }
                sampleRows.add(rowData);
            }
            return new ImportPreviewResponse(headers, sampleRows, lastRow);
        }
    }

    private ImportPreviewResponse previewCsv(MultipartFile file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return new ImportPreviewResponse(List.of(), List.of(), 0);

            String[] headerArr = splitCsvLine(headerLine);
            List<String> headers = new ArrayList<>();
            for (String h : headerArr) headers.add(cleanValue(h));

            List<List<String>> sampleRows = new ArrayList<>();
            int totalRows = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                totalRows++;
                if (sampleRows.size() < 5) {
                    String[] values = splitCsvLine(line);
                    List<String> rowData = new ArrayList<>();
                    for (int i = 0; i < headers.size(); i++) {
                        rowData.add(i < values.length ? cleanValue(values[i]) : "");
                    }
                    sampleRows.add(rowData);
                }
            }
            return new ImportPreviewResponse(headers, sampleRows, totalRows);
        }
    }

    // ── Private: mapping-based parsers ───────────────────────────────────────

    private List<Map<String, String>> parseExcelWithMapping(MultipartFile file,
                                                            Map<String, Integer> mapping) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) return rows;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {   // row 0 = headers, skip
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> rowData = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : mapping.entrySet()) {
                    if (entry.getValue() != null) {
                        // Lowercase key so existing extractField() lookups match
                        rowData.put(entry.getKey().toLowerCase(),
                                cleanValue(getCellValue(row.getCell(entry.getValue()))));
                    }
                }
                rows.add(rowData);
            }
        }
        return rows;
    }

    private List<Map<String, String>> parseCsvWithMapping(MultipartFile file,
                                                          Map<String, Integer> mapping) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            reader.readLine(); // skip header row
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = splitCsvLine(line);
                Map<String, String> rowData = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : mapping.entrySet()) {
                    if (entry.getValue() != null) {
                        int idx = entry.getValue();
                        rowData.put(entry.getKey().toLowerCase(),
                                idx < values.length ? cleanValue(values[idx]) : "");
                    }
                }
                rows.add(rowData);
            }
        }
        return rows;
    }

    // ── Private: shared file-validation helpers ───────────────────────────────

    private static String resolvedFilename(MultipartFile file) {
        return file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
    }

    private static void validateFilename(String filename) {
        if (filename.endsWith(".xls")) {
            throw new IllegalArgumentException(
                    "Old Excel format (.xls) is not supported. Please save the file as .xlsx and re-upload.");
        }
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".csv")) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Please upload a .xlsx or .csv file.");
        }
    }

    /**
     * Marks the contact UNSUBSCRIBED and adds them to the suppression list so
     * re-imports cannot accidentally re-enable sending to an opted-out address.
     *
     * @return the contact if found (so the caller can update campaign stats / tracking)
     */
    public Optional<Contact> unsubscribe(String token) {
        Optional<Contact> found = contactRepository.findByUnsubscribeToken(token);
        found.ifPresent(contact -> {
            if (contact.getStatus() != Contact.ContactStatus.UNSUBSCRIBED) {
                contact.setStatus(Contact.ContactStatus.UNSUBSCRIBED);
                contact.setUnsubscribedAt(java.time.LocalDateTime.now());
                contactRepository.save(contact);
            }
            // Suppress regardless of prior status so re-imports can't override the opt-out
            Long orgId = contact.getOrganizationId();
            boolean alreadySuppressed = orgId != null
                    ? suppressionListRepository.existsByOrganizationIdAndEmailIgnoreCase(orgId, contact.getEmail())
                    : suppressionListRepository.existsByOrganizationIdIsNullAndEmailIgnoreCase(contact.getEmail());
            if (!alreadySuppressed) {
                suppressionListRepository.save(new SuppressionList(
                        orgId, contact.getEmail(),
                        SuppressionList.Reason.UNSUBSCRIBE, "link-click"));
            }
        });
        return found;
    }

    /**
     * Reverses an accidental unsubscribe. Removes the suppression entry and
     * restores the contact to ACTIVE. Only valid while the token is still trusted.
     */
    public Optional<Contact> resubscribe(String token) {
        Optional<Contact> found = contactRepository.findByUnsubscribeToken(token);
        found.ifPresent(contact -> {
            contact.setStatus(Contact.ContactStatus.ACTIVE);
            contact.setUnsubscribedAt(null);
            contactRepository.save(contact);
            // Remove the suppression entry so future sends are allowed
            Long orgId = contact.getOrganizationId();
            suppressionListRepository.findByEmailAndOrg(contact.getEmail(), orgId != null ? orgId : -1L)
                    .ifPresent(suppressionListRepository::delete);
        });
        return found;
    }

    @Cacheable(value = "contact-stats", key = "T(com.bulkemail.pro.security.TenantContext).getOrganizationId()")
    public Map<String, Long> getStats() {
        Long orgId = TenantContext.getOrganizationId();
        Map<String, Long> stats = new HashMap<>();
        for (Contact.ContactStatus status : Contact.ContactStatus.values()) {
            stats.put(status.name().toLowerCase(),
                orgId != null
                    ? contactRepository.countByStatusAndOrganizationId(status, orgId)
                    : contactRepository.countByStatus(status));
        }
        stats.put("total", orgId != null
            ? contactRepository.countByOrganizationId(orgId)
            : contactRepository.count());
        return stats;
    }
}
