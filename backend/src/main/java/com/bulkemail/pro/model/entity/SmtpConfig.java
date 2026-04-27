package com.bulkemail.pro.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "smtp_configs")
@Data
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "encryptedPassword"})
public class SmtpConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port = 587;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String encryptedPassword;

    @Enumerated(EnumType.STRING)
    private SecurityType securityType = SecurityType.TLS;

    @Enumerated(EnumType.STRING)
    private ProviderType providerType = ProviderType.CUSTOM;

    private String fromName;
    private String fromEmail;
    private String replyToEmail;

    private Integer dailyLimit = 500;
    private Integer hourlyLimit = 100;
    private Integer sentToday = 0;
    private Integer sentThisHour = 0;

    private LocalDate lastResetDate;
    private LocalDateTime lastResetHour;

    private boolean isDefault = false;
    private boolean isActive = true;
    private boolean connectionTested = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum SecurityType {
        NONE, TLS, SSL
    }

    public enum ProviderType {
        CUSTOM, GMAIL, AMAZON_SES, BREVO, MAILGUN, SENDGRID
    }
}
