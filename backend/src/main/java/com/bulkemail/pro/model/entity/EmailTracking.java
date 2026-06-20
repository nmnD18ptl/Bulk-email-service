package com.bulkemail.pro.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_tracking", indexes = {
    @Index(name = "idx_tracking_id", columnList = "trackingId"),
    @Index(name = "idx_tracking_campaign", columnList = "campaign_id"),
    @Index(name = "idx_tracking_contact", columnList = "contact_id")
})
@Data
@NoArgsConstructor
public class EmailTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(nullable = false, unique = true)
    private String trackingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_id")
    private EmailQueue emailQueue;

    @Enumerated(EnumType.STRING)
    private TrackingEvent eventType;

    private String originalUrl;
    private String ipAddress;
    private String userAgent;
    private String country;

    private boolean firstOpen = false;
    private boolean firstClick = false;

    @CreationTimestamp
    private LocalDateTime eventAt;

    public enum TrackingEvent {
        SENT, DELIVERED, OPENED, CLICKED, BOUNCED, COMPLAINED, UNSUBSCRIBED
    }
}
