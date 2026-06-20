package com.bulkemail.pro.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Immutable audit ledger — rows are never updated or deleted.
 * Written inside the same transaction as the domain change so
 * the log is always consistent with the data it describes.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 100)
    private String action;          // e.g. CONTACT_CREATED, CAMPAIGN_SENT

    @Column(name = "entity_type", length = 100)
    private String entityType;      // e.g. Contact, Campaign

    @Column(name = "entity_id")
    private Long entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private Map<String, Object> oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private Map<String, Object> newValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AuditLog() {}

    private AuditLog(Builder b) {
        this.organizationId = b.organizationId;
        this.userId         = b.userId;
        this.action         = b.action;
        this.entityType     = b.entityType;
        this.entityId       = b.entityId;
        this.oldValue       = b.oldValue;
        this.newValue       = b.newValue;
        this.ipAddress      = b.ipAddress;
        this.userAgent      = b.userAgent;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long organizationId;
        private Long userId;
        private String action;
        private String entityType;
        private Long entityId;
        private Map<String, Object> oldValue;
        private Map<String, Object> newValue;
        private String ipAddress;
        private String userAgent;

        public Builder organizationId(Long v)           { this.organizationId = v; return this; }
        public Builder userId(Long v)                   { this.userId = v;         return this; }
        public Builder action(String v)                 { this.action = v;         return this; }
        public Builder entityType(String v)             { this.entityType = v;     return this; }
        public Builder entityId(Long v)                 { this.entityId = v;       return this; }
        public Builder oldValue(Map<String, Object> v)  { this.oldValue = v;       return this; }
        public Builder newValue(Map<String, Object> v)  { this.newValue = v;       return this; }
        public Builder ipAddress(String v)              { this.ipAddress = v;      return this; }
        public Builder userAgent(String v)              { this.userAgent = v;      return this; }
        public AuditLog build()                         { return new AuditLog(this); }
    }

    // Getters — no setters; this object is immutable after construction
    public Long getId()                        { return id; }
    public Long getOrganizationId()            { return organizationId; }
    public Long getUserId()                    { return userId; }
    public String getAction()                  { return action; }
    public String getEntityType()              { return entityType; }
    public Long getEntityId()                  { return entityId; }
    public Map<String, Object> getOldValue()   { return oldValue; }
    public Map<String, Object> getNewValue()   { return newValue; }
    public String getIpAddress()               { return ipAddress; }
    public String getUserAgent()               { return userAgent; }
    public OffsetDateTime getCreatedAt()       { return createdAt; }
}
