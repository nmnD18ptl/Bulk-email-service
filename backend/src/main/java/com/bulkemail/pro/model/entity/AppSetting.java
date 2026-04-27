package com.bulkemail.pro.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "settings")
@Data
@NoArgsConstructor
public class AppSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(nullable = false)
    private String settingKey;

    @Column(columnDefinition = "TEXT")
    private String settingValue;

    private String description;
    private String category;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public AppSetting(String key, String value, String category, String description) {
        this.settingKey = key;
        this.settingValue = value;
        this.category = category;
        this.description = description;
    }
}
