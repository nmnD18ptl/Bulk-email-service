package com.bulkemail.pro.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_queue", indexes = {
    @Index(name = "idx_queue_campaign", columnList = "campaign_id"),
    @Index(name = "idx_queue_status", columnList = "status"),
    @Index(name = "idx_queue_contact", columnList = "contact_id")
})
@Data
@NoArgsConstructor
public class EmailQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id")
    private Long organizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @Column(nullable = false)
    private String recipientEmail;

    private String recipientName;

    @Column(columnDefinition = "TEXT")
    private String personalizedSubject;

    @Column(columnDefinition = "TEXT")
    private String personalizedHtml;

    @Column(columnDefinition = "TEXT")
    private String personalizedText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueueStatus status = QueueStatus.PENDING;

    private Integer priority = 5;
    private Integer retryCount = 0;
    private Integer maxRetries = 3;

    private String trackingId;
    private String messageId;
    private String errorMessage;

    private LocalDateTime scheduledAt;
    private LocalDateTime sentAt;
    private LocalDateTime lastAttemptAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum QueueStatus {
        PENDING, SENDING, SENT, FAILED, BOUNCED, CANCELLED, SKIPPED
    }
}
