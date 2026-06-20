package com.bulkemail.pro.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Transactional Outbox pattern.
 * Written in the same DB transaction as the domain change; a separate
 * relay process reads PENDING rows and publishes them to RabbitMQ (P3).
 * Guarantees at-least-once delivery with no dual-write race.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    public enum Status { PENDING, PUBLISHED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;       // e.g. CAMPAIGN, CONTACT

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;           // e.g. CAMPAIGN_SEND_REQUESTED

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(String aggregateType, Long aggregateId, String eventType,
                       Map<String, Object> payload, Long organizationId) {
        this.aggregateType  = aggregateType;
        this.aggregateId    = aggregateId;
        this.eventType      = eventType;
        this.payload        = payload;
        this.organizationId = organizationId;
    }

    public void markPublished() {
        this.status      = Status.PUBLISHED;
        this.publishedAt = OffsetDateTime.now();
    }

    public void markFailed(String error) {
        this.status       = Status.FAILED;
        this.errorMessage = error;
        this.retryCount++;
    }

    public void resetToPending() {
        this.status = Status.PENDING;
    }

    // Getters
    public Long getId()                    { return id; }
    public String getAggregateType()       { return aggregateType; }
    public Long getAggregateId()           { return aggregateId; }
    public String getEventType()           { return eventType; }
    public Map<String, Object> getPayload(){ return payload; }
    public Long getOrganizationId()        { return organizationId; }
    public Status getStatus()              { return status; }
    public int getRetryCount()             { return retryCount; }
    public String getErrorMessage()        { return errorMessage; }
    public OffsetDateTime getCreatedAt()   { return createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
}
