package com.bulkemail.pro.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.util.*;

@RestController
@RequestMapping("/api/domain")
@Slf4j
public class DomainHealthController {

    private static final String[] COMMON_DKIM_SELECTORS = {
        "default", "google", "mail", "dkim", "k1", "key1", "selector1", "selector2",
        "smtp", "s1", "s2", "em", "mandrill", "mailchimp", "sendgrid", "brevo"
    };

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<Map<String, Object>> checkDomainHealth(@RequestParam String domain) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domain", domain);

        Map<String, Object> spfResult   = checkSpf(domain);
        Map<String, Object> dmarcResult = checkDmarc(domain);
        Map<String, Object> dkimResult  = checkDkim(domain);
        result.put("spf", spfResult);
        result.put("dmarc", dmarcResult);
        result.put("dkim", dkimResult);

        List<Map<String, Object>> checks = List.of(spfResult, dmarcResult, dkimResult);
        boolean allValid = checks.stream().allMatch(c -> Boolean.TRUE.equals(c.get("valid")));
        boolean anyValid = checks.stream().anyMatch(c -> Boolean.TRUE.equals(c.get("valid")));
        result.put("overallStatus", allValid ? "GOOD" : anyValid ? "WARNING" : "POOR");

        return ResponseEntity.ok(result);
    }

    private Map<String, Object> checkSpf(String domain) {
        Map<String, Object> spf = new LinkedHashMap<>();
        spf.put("type", "SPF");
        try {
            Record[] records = new Lookup(domain, Type.TXT).run();
            if (records != null) {
                for (Record r : records) {
                    String txt = ((TXTRecord) r).rdataToString().replace("\"", "");
                    if (txt.startsWith("v=spf1")) {
                        spf.put("valid", true);
                        spf.put("record", txt);
                        spf.put("message", "SPF record found");
                        return spf;
                    }
                }
            }
            spf.put("valid", false);
            spf.put("record", null);
            spf.put("message", "No SPF record found. Add: v=spf1 include:your-smtp-provider.com ~all");
        } catch (Exception e) {
            log.debug("SPF lookup failed for {}: {}", domain, e.getMessage());
            spf.put("valid", false);
            spf.put("record", null);
            spf.put("message", "SPF lookup failed: " + e.getMessage());
        }
        return spf;
    }

    private Map<String, Object> checkDmarc(String domain) {
        Map<String, Object> dmarc = new LinkedHashMap<>();
        dmarc.put("type", "DMARC");
        try {
            Record[] records = new Lookup("_dmarc." + domain, Type.TXT).run();
            if (records != null) {
                for (Record r : records) {
                    String txt = ((TXTRecord) r).rdataToString().replace("\"", "");
                    if (txt.startsWith("v=DMARC1")) {
                        dmarc.put("valid", true);
                        dmarc.put("record", txt);
                        dmarc.put("message", "DMARC record found");
                        return dmarc;
                    }
                }
            }
            dmarc.put("valid", false);
            dmarc.put("record", null);
            dmarc.put("message", "No DMARC record found. Add TXT record at _dmarc." + domain
                + " with value: v=DMARC1; p=none; rua=mailto:dmarc@" + domain);
        } catch (Exception e) {
            log.debug("DMARC lookup failed for {}: {}", domain, e.getMessage());
            dmarc.put("valid", false);
            dmarc.put("record", null);
            dmarc.put("message", "DMARC lookup failed: " + e.getMessage());
        }
        return dmarc;
    }

    private Map<String, Object> checkDkim(String domain) {
        Map<String, Object> dkim = new LinkedHashMap<>();
        dkim.put("type", "DKIM");
        for (String selector : COMMON_DKIM_SELECTORS) {
            try {
                String dkimHost = selector + "._domainkey." + domain;
                Record[] records = new Lookup(dkimHost, Type.TXT).run();
                if (records != null) {
                    for (Record r : records) {
                        String txt = ((TXTRecord) r).rdataToString().replace("\"", "");
                        if (txt.contains("v=DKIM1") || txt.contains("p=")) {
                            dkim.put("valid", true);
                            dkim.put("selector", selector);
                            dkim.put("record", txt.length() > 100 ? txt.substring(0, 100) + "..." : txt);
                            dkim.put("message", "DKIM record found (selector: " + selector + ")");
                            return dkim;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        dkim.put("valid", false);
        dkim.put("selector", null);
        dkim.put("record", null);
        dkim.put("message", "No DKIM record found for common selectors. "
            + "Configure DKIM signing in your email provider and add the TXT record.");
        return dkim;
    }
}
