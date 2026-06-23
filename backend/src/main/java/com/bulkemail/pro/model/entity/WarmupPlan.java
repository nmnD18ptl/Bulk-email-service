package com.bulkemail.pro.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "warmup_plans")
@Data
@NoArgsConstructor
public class WarmupPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "smtp_config_id")
    private SmtpConfig smtpConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WarmupStatus status = WarmupStatus.NOT_STARTED;

    private Integer targetDailyVolume = 5000;
    private Integer currentStage = 1;
    private Integer totalStages = 14;
    private Integer currentDayVolume = 0;

    private LocalDate startDate;
    private LocalDate estimatedCompletionDate;
    private LocalDate lastRunDate;

    // Warmup schedule stored as JSON
    @Column(columnDefinition = "TEXT")
    private String scheduleJson;

    // Metrics for auto-pause decision
    private Double bounceRateThreshold = 2.0;
    private Double complaintRateThreshold = 0.1;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum WarmupStatus {
        NOT_STARTED, ACTIVE, PAUSED, COMPLETED, FAILED
    }
}
