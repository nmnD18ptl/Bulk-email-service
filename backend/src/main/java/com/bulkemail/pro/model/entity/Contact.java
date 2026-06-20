package com.bulkemail.pro.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "contacts", indexes = {
    @Index(name = "idx_contact_email", columnList = "email"),
    @Index(name = "idx_contact_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(nullable = false)
    private String email;

    private String firstName;
    private String lastName;
    private String company;
    private String country;
    private String phone;

    private String customField1;
    private String customField2;
    private String customField3;
    private String customField4;
    private String customField5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactStatus status = ContactStatus.ACTIVE;

    private boolean emailVerified = false;
    private boolean mxRecordValid = false;

    private Integer engagementScore = 0;
    private LocalDateTime lastOpenedAt;
    private LocalDateTime lastClickedAt;
    private LocalDateTime subscribedAt;
    private LocalDateTime unsubscribedAt;

    private String unsubscribeToken;
    private String optInSource;
    private boolean gdprConsent = false;
    private LocalDateTime gdprConsentDate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "contact_tags",
        joinColumns = @JoinColumn(name = "contact_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    public enum ContactStatus {
        ACTIVE, UNSUBSCRIBED, BOUNCED, COMPLAINED, INVALID
    }

    public String getFullName() {
        if (firstName != null && lastName != null) return firstName + " " + lastName;
        if (firstName != null) return firstName;
        if (lastName != null) return lastName;
        return email;
    }
}
