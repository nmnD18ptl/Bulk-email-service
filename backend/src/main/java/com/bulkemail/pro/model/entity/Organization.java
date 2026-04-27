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
@Table(name = "organizations")
@Data
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType plan = PlanType.FREE;

    private Integer monthlyEmailLimit = 500;
    private Integer emailsSentThisMonth = 0;
    private Integer maxContacts = 500;
    private Integer maxSmtpConfigs = 1;

    private LocalDate billingCycleStart;
    private boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum PlanType {
        FREE,           // 500 emails/month, 500 contacts, 1 SMTP
        STARTER,        // 5,000 emails/month, 5,000 contacts, 2 SMTP
        PROFESSIONAL,   // 50,000 emails/month, 50,000 contacts, 5 SMTP
        ENTERPRISE      // unlimited, 10 SMTP
    }

    public static Organization createWithPlan(String name, String slug, PlanType plan) {
        Organization org = new Organization();
        org.setName(name);
        org.setSlug(slug);
        org.setPlan(plan);
        org.setBillingCycleStart(LocalDate.now());
        switch (plan) {
            case FREE          -> { org.setMonthlyEmailLimit(500);    org.setMaxContacts(500);    org.setMaxSmtpConfigs(1);  }
            case STARTER       -> { org.setMonthlyEmailLimit(5000);   org.setMaxContacts(5000);   org.setMaxSmtpConfigs(2);  }
            case PROFESSIONAL  -> { org.setMonthlyEmailLimit(50000);  org.setMaxContacts(50000);  org.setMaxSmtpConfigs(5);  }
            case ENTERPRISE    -> { org.setMonthlyEmailLimit(Integer.MAX_VALUE); org.setMaxContacts(Integer.MAX_VALUE); org.setMaxSmtpConfigs(10); }
        }
        return org;
    }
}
