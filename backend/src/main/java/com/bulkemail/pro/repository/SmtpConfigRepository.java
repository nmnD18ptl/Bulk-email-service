package com.bulkemail.pro.repository;

import com.bulkemail.pro.model.entity.SmtpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SmtpConfigRepository extends JpaRepository<SmtpConfig, Long> {

    Optional<SmtpConfig> findByIsDefaultTrue();

    @Query("SELECT s FROM SmtpConfig s WHERE s.isActive = true AND s.isDefault = true")
    Optional<SmtpConfig> findActiveDefault();

    List<SmtpConfig> findByOrganizationId(Long organizationId);

    long countByOrganizationId(Long organizationId);

    @Query("SELECT s FROM SmtpConfig s WHERE s.organizationId = :orgId AND s.isActive = true AND s.isDefault = true")
    Optional<SmtpConfig> findActiveDefaultByOrganizationId(@Param("orgId") Long orgId);
}
