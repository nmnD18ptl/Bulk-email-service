package com.bulkemail.pro.service;

import com.bulkemail.pro.model.entity.SmtpConfig;
import com.bulkemail.pro.repository.SmtpConfigRepository;
import com.bulkemail.pro.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SmtpConfigService {

    private final SmtpConfigRepository smtpConfigRepository;
    private final EncryptionService encryptionService;
    private final SmtpRateLimiterService smtpRateLimiterService;
    private final BrevoApiService brevoApiService;

    public boolean isBrevoApi(SmtpConfig config) {
        return SmtpConfig.ProviderType.BREVO == config.getProviderType();
    }

    public String getDecryptedApiKey(SmtpConfig config) {
        return encryptionService.decrypt(config.getEncryptedPassword());
    }

    public List<SmtpConfig> findAll() {
        Long orgId = TenantContext.getOrganizationId();
        return orgId != null
            ? smtpConfigRepository.findByOrganizationId(orgId)
            : smtpConfigRepository.findAll();
    }

    public Optional<SmtpConfig> findById(Long id) {
        Long orgId = TenantContext.getOrganizationId();
        return smtpConfigRepository.findById(id)
            .filter(c -> orgId == null || orgId.equals(c.getOrganizationId()));
    }

    public SmtpConfig save(SmtpConfig config, String rawPassword) {
        Long orgId = TenantContext.getOrganizationId();
        if (orgId != null) {
            config.setOrganizationId(orgId);
        }
        if (rawPassword != null && !rawPassword.isEmpty()) {
            config.setEncryptedPassword(encryptionService.encrypt(rawPassword));
        }

        if (config.isDefault()) {
            // Unset other defaults within this org
            List<SmtpConfig> existing = orgId != null
                ? smtpConfigRepository.findByOrganizationId(orgId)
                : smtpConfigRepository.findAll();
            existing.forEach(c -> {
                if (c.isDefault() && !c.getId().equals(config.getId())) {
                    c.setDefault(false);
                    smtpConfigRepository.save(c);
                }
            });
        }

        return smtpConfigRepository.save(config);
    }

    public void delete(Long id) {
        findById(id).ifPresent(c -> smtpConfigRepository.deleteById(id));
    }

    public boolean testConnection(Long id) {
        return smtpConfigRepository.findById(id).map(config -> {
            try {
                JavaMailSenderImpl sender = buildMailSender(config);
                sender.testConnection();
                config.setConnectionTested(true);
                smtpConfigRepository.save(config);
                return true;
            } catch (Exception e) {
                log.error("SMTP connection test failed for {}: {}", config.getName(), e.getMessage());
                return false;
            }
        }).orElse(false);
    }

    /**
     * Returns null on success, or the error message string on failure.
     * When providerType is BREVO, tests the API key via HTTPS instead of SMTP.
     */
    public String testConnectionWithParams(String host, int port, String username, String password,
                                           SmtpConfig.SecurityType securityType,
                                           SmtpConfig.ProviderType providerType) {
        if (providerType == SmtpConfig.ProviderType.BREVO) {
            return brevoApiService.testApiKey(password);
        }
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(port);
            sender.setUsername(username);
            sender.setPassword(password);
            configureProperties(sender, securityType);
            sender.testConnection();
            return null;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getSimpleName();
            log.error("SMTP connection test failed for {}:{} — {}", host, port, msg);
            return msg;
        }
    }

    public String testConnectionWithParams(String host, int port, String username, String password,
                                           SmtpConfig.SecurityType securityType) {
        return testConnectionWithParams(host, port, username, password, securityType, SmtpConfig.ProviderType.CUSTOM);
    }

    public JavaMailSenderImpl buildMailSender(SmtpConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort());
        sender.setUsername(config.getUsername());
        sender.setPassword(encryptionService.decrypt(config.getEncryptedPassword()));
        sender.setDefaultEncoding("UTF-8");
        configureProperties(sender, config.getSecurityType());
        return sender;
    }

    private void configureProperties(JavaMailSenderImpl sender, SmtpConfig.SecurityType securityType) {
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        switch (securityType) {
            case TLS -> {
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
            case SSL -> {
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            default -> {
                props.put("mail.smtp.auth", "true");
            }
        }
    }

    public Optional<SmtpConfig> getDefault() {
        Long orgId = TenantContext.getOrganizationId();
        return orgId != null
            ? smtpConfigRepository.findActiveDefaultByOrganizationId(orgId)
            : smtpConfigRepository.findActiveDefault();
    }

    /**
     * Checks both daily and hourly Redis counters.
     * Replaces the former in-memory sentToday/sentThisHour fields which broke
     * under horizontal scaling (each instance held its own counter).
     */
    public boolean canSendMore(SmtpConfig config) {
        int daily  = config.getDailyLimit()  != null ? config.getDailyLimit()  : Integer.MAX_VALUE;
        int hourly = config.getHourlyLimit() != null ? config.getHourlyLimit() : Integer.MAX_VALUE;
        return smtpRateLimiterService.canSend(config.getId(), daily, hourly);
    }

    /**
     * Atomically increments the Redis daily and hourly counters.
     * No DB write needed — Redis INCR is the source of truth for rate limiting.
     */
    public void incrementSentCount(SmtpConfig config) {
        smtpRateLimiterService.recordSent(config.getId());
    }
}
