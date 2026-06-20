package com.bulkemail.pro.model.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Global and per-org email suppression.
 * organizationId = null means the suppression is platform-wide
 * (e.g. a known spam trap address).
 */
@Entity
@Table(
    name = "suppression_list",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_suppression_org_email",
        columnNames = {"organization_id", "email"}
    )
)
public class SuppressionList {

    public enum Reason { BOUNCE, COMPLAINT, UNSUBSCRIBE, MANUAL, SPAM_TRAP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id")
    private Long organizationId;            // null = platform-wide

    @Column(nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Reason reason;

    @Column(length = 100)
    private String source;                  // e.g. "campaign:42", "webhook:sendgrid"

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected SuppressionList() {}

    public SuppressionList(Long organizationId, String email, Reason reason, String source) {
        this.organizationId = organizationId;
        this.email          = email.toLowerCase().trim();
        this.reason         = reason;
        this.source         = source;
    }

    public Long getId()                { return id; }
    public Long getOrganizationId()    { return organizationId; }
    public String getEmail()           { return email; }
    public Reason getReason()          { return reason; }
    public String getSource()          { return source; }
    public OffsetDateTime getCreatedAt(){ return createdAt; }
}
