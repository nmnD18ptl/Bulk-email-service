package com.bulkemail.pro.repository;

import com.bulkemail.pro.model.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId, Pageable pageable);

    Page<AuditLog> findByOrganizationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            Long organizationId, String entityType, Long entityId, Pageable pageable);

    List<AuditLog> findByOrganizationIdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long organizationId, OffsetDateTime since);
}
