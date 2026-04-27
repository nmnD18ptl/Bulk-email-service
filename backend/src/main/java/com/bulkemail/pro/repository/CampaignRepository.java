package com.bulkemail.pro.repository;

import com.bulkemail.pro.model.entity.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    Page<Campaign> findByStatus(Campaign.CampaignStatus status, Pageable pageable);

    List<Campaign> findByStatusOrderByCreatedAtDesc(Campaign.CampaignStatus status);

    @Query("SELECT c FROM Campaign c LEFT JOIN FETCH c.smtpConfig WHERE c.id = :id")
    Optional<Campaign> findByIdWithSmtpConfig(@Param("id") Long id);

    @Query("SELECT c FROM Campaign c WHERE c.status = 'SCHEDULED' AND c.scheduledAt <= :now AND c.organizationId = :orgId")
    List<Campaign> findDueCampaignsByOrg(@Param("now") LocalDateTime now, @Param("orgId") Long orgId);

    // Tenant-scoped queries
    Page<Campaign> findByOrganizationId(Long organizationId, Pageable pageable);

    @Query("SELECT c FROM Campaign c WHERE c.organizationId = :orgId ORDER BY c.createdAt DESC")
    List<Campaign> findRecentByOrganizationId(@Param("orgId") Long orgId, Pageable pageable);

    @Query("SELECT SUM(c.sentCount), SUM(c.openCount), SUM(c.clickCount), SUM(c.bounceCount) FROM Campaign c WHERE c.status = 'COMPLETED' AND c.organizationId = :orgId")
    Object[] getOverallStatsByOrganizationId(@Param("orgId") Long orgId);

    long countByOrganizationId(Long organizationId);
}
