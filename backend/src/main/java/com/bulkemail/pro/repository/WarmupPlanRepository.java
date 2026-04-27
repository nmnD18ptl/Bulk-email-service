package com.bulkemail.pro.repository;

import com.bulkemail.pro.model.entity.WarmupPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarmupPlanRepository extends JpaRepository<WarmupPlan, Long> {
    List<WarmupPlan> findByStatus(WarmupPlan.WarmupStatus status);
    List<WarmupPlan> findBySmtpConfigId(Long smtpConfigId);
    List<WarmupPlan> findByOrganizationId(Long organizationId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT w FROM WarmupPlan w WHERE w.smtpConfig.id = :smtpConfigId AND w.status = 'ACTIVE'")
    Optional<WarmupPlan> findActiveBySmtpConfigId(@org.springframework.data.repository.query.Param("smtpConfigId") Long smtpConfigId);
}
