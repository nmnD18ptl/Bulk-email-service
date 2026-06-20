package com.bulkemail.pro.repository;

import com.bulkemail.pro.model.entity.SuppressionList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SuppressionListRepository extends JpaRepository<SuppressionList, Long> {

    /** Check org-specific OR platform-wide suppression in one query. */
    @Query("""
        SELECT s FROM SuppressionList s
        WHERE LOWER(s.email) = LOWER(:email)
          AND (s.organizationId = :orgId OR s.organizationId IS NULL)
        """)
    Optional<SuppressionList> findByEmailAndOrg(
            @Param("email") String email,
            @Param("orgId") Long orgId);

    boolean existsByOrganizationIdAndEmailIgnoreCase(Long organizationId, String email);

    boolean existsByOrganizationIdIsNullAndEmailIgnoreCase(String email);

    long countByOrganizationId(Long organizationId);
}
