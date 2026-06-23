package com.bulkemail.pro.controller;

import com.bulkemail.pro.model.entity.SmtpConfig;
import com.bulkemail.pro.service.SmtpConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/smtp-configs")
@RequiredArgsConstructor
public class SmtpConfigController {

    private final SmtpConfigService smtpConfigService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<List<SmtpConfig>> list() {
        List<SmtpConfig> configs = smtpConfigService.findAll();
        // Mask passwords in response
        configs.forEach(c -> c.setEncryptedPassword("***"));
        return ResponseEntity.ok(configs);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<SmtpConfig> getById(@PathVariable Long id) {
        return smtpConfigService.findById(id).map(config -> {
            config.setEncryptedPassword("***");
            return ResponseEntity.ok(config);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<SmtpConfig> create(
            @RequestBody Map<String, Object> body) {
        SmtpConfig config = mapToConfig(body, new SmtpConfig());
        String rawPassword = (String) body.get("password");
        SmtpConfig saved = smtpConfigService.save(config, rawPassword);
        saved.setEncryptedPassword("***");
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<SmtpConfig> update(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return smtpConfigService.findById(id).map(existing -> {
            mapToConfig(body, existing);
            String rawPassword = (String) body.get("password");
            SmtpConfig saved = smtpConfigService.save(existing, rawPassword);
            saved.setEncryptedPassword("***");
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        smtpConfigService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        boolean success = smtpConfigService.testConnection(id);
        return ResponseEntity.ok(Map.of(
            "success", success,
            "message", success ? "Connection successful!" : "Connection failed. Check your settings."
        ));
    }

    @PostMapping("/test-params")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Map<String, Object>> testConnectionWithParams(
            @RequestBody Map<String, Object> body) {
        String host = (String) body.get("host");
        int port = Integer.parseInt(body.get("port").toString());
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        String security = (String) body.getOrDefault("securityType", "TLS");

        String error = smtpConfigService.testConnectionWithParams(
            host, port, username, password,
            SmtpConfig.SecurityType.valueOf(security));

        boolean success = (error == null);
        return ResponseEntity.ok(Map.of(
            "success", success,
            "message", success ? "Connection successful!" : "Connection failed: " + error
        ));
    }

    @PostMapping("/ping")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Map<String, Object>> pingHost(@RequestBody Map<String, Object> body) {
        String host = (String) body.get("host");
        int port = Integer.parseInt(body.get("port").toString());
        long start = System.currentTimeMillis();
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 10000);
            long ms = System.currentTimeMillis() - start;
            return ResponseEntity.ok(Map.of(
                "reachable", true,
                "message", "TCP connection to " + host + ":" + port + " succeeded in " + ms + "ms"
            ));
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            return ResponseEntity.ok(Map.of(
                "reachable", false,
                "message", "TCP connection to " + host + ":" + port + " failed after " + ms + "ms: " + e.getMessage()
            ));
        }
    }

    private SmtpConfig mapToConfig(Map<String, Object> body, SmtpConfig config) {
        if (body.containsKey("name")) config.setName((String) body.get("name"));
        if (body.containsKey("host")) config.setHost((String) body.get("host"));
        if (body.containsKey("port")) config.setPort(Integer.parseInt(body.get("port").toString()));
        if (body.containsKey("username")) config.setUsername((String) body.get("username"));
        if (body.containsKey("fromName")) config.setFromName((String) body.get("fromName"));
        if (body.containsKey("fromEmail")) config.setFromEmail((String) body.get("fromEmail"));
        if (body.containsKey("replyToEmail")) config.setReplyToEmail((String) body.get("replyToEmail"));
        if (body.containsKey("dailyLimit"))
            config.setDailyLimit(Integer.parseInt(body.get("dailyLimit").toString()));
        if (body.containsKey("hourlyLimit"))
            config.setHourlyLimit(Integer.parseInt(body.get("hourlyLimit").toString()));
        if (body.containsKey("isDefault"))
            config.setDefault(Boolean.parseBoolean(body.get("isDefault").toString()));
        if (body.containsKey("securityType"))
            config.setSecurityType(SmtpConfig.SecurityType.valueOf((String) body.get("securityType")));
        if (body.containsKey("providerType"))
            config.setProviderType(SmtpConfig.ProviderType.valueOf((String) body.get("providerType")));
        return config;
    }
}
