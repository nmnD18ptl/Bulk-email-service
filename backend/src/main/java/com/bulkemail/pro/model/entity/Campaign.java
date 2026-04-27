package com.bulkemail.pro.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "campaigns")
@Data
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(nullable = false)
    private String name;

    private String subject;

    private String previewText;

    @Column(columnDefinition = "TEXT")
    private String htmlContent;

    @Column(columnDefinition = "TEXT")
    private String textContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "smtp_config_id")
    private SmtpConfig smtpConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private Template template;

    // Target audience
    @ElementCollection
    @CollectionTable(name = "campaign_tag_filters", joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "tag_id")
    private List<Long> tagFilters = new ArrayList<>();

    private boolean sendToAll = true;

    // Sender info
    private String fromName;
    private String fromEmail;
    private String replyToEmail;

    // Scheduling
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    // Stats
    private Integer totalRecipients = 0;
    private Integer sentCount = 0;
    private Integer deliveredCount = 0;
    private Integer openCount = 0;
    private Integer clickCount = 0;
    private Integer bounceCount = 0;
    private Integer unsubscribeCount = 0;
    private Integer complaintCount = 0;
    private Integer failedCount = 0;

    // Batch settings
    private Integer batchSize = 100;
    private Integer batchDelaySeconds = 60;
    private Integer interEmailDelayMs = 200;
    private Integer maxRetries = 3;

    // Physical address (CAN-SPAM)
    private String physicalAddress;

    // Tracking
    private boolean trackOpens = true;
    private boolean trackClicks = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum CampaignStatus {
        DRAFT, SCHEDULED, SENDING, PAUSED, COMPLETED, CANCELLED, FAILED
    }

    public double getOpenRate() {
        if (sentCount == null || sentCount == 0) return 0;
        return (double) openCount / sentCount * 100;
    }

    public double getClickRate() {
        if (sentCount == null || sentCount == 0) return 0;
        return (double) clickCount / sentCount * 100;
    }

    public double getBounceRate() {
        if (totalRecipients == null || totalRecipients == 0) return 0;
        return (double) bounceCount / totalRecipients * 100;
    }
}
