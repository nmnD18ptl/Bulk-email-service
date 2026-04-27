package com.bulkemail.pro.repository;

import com.bulkemail.pro.model.entity.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<Contact> findByEmailAndOrganizationId(String email, Long organizationId);
    boolean existsByEmailAndOrganizationId(String email, Long organizationId);

    Page<Contact> findByStatus(Contact.ContactStatus status, Pageable pageable);

    @Query("SELECT c FROM Contact c WHERE " +
           "(:search IS NULL OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.company) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND c.organizationId = :orgId")
    Page<Contact> findByOrganizationIdAndSearchAndStatus(
        @Param("orgId") Long orgId,
        @Param("search") String search,
        @Param("status") Contact.ContactStatus status,
        Pageable pageable
    );

    @Query("SELECT c FROM Contact c JOIN c.tags t WHERE t.id = :tagId AND c.status = 'ACTIVE' AND c.organizationId = :orgId")
    List<Contact> findByTagIdAndActiveAndOrganizationId(@Param("tagId") Long tagId, @Param("orgId") Long orgId);

    @Query("SELECT c FROM Contact c WHERE c.status = 'ACTIVE' AND c.organizationId = :orgId ORDER BY c.engagementScore DESC")
    List<Contact> findActiveByOrganizationId(@Param("orgId") Long orgId);

    @Query("SELECT c FROM Contact c WHERE c.status = 'ACTIVE'")
    List<Contact> findAllActive();

    @Query("SELECT c FROM Contact c WHERE c.status = 'ACTIVE' AND c.organizationId = :orgId")
    List<Contact> findAllActiveByOrganizationId(@Param("orgId") Long orgId);

    @Query("SELECT COUNT(c) FROM Contact c WHERE c.status = :status AND c.organizationId = :orgId")
    long countByStatusAndOrganizationId(@Param("status") Contact.ContactStatus status, @Param("orgId") Long orgId);

    long countByOrganizationId(Long organizationId);

    @Query("SELECT COUNT(c) FROM Contact c WHERE c.status = :status")
    long countByStatus(@Param("status") Contact.ContactStatus status);

    List<Contact> findByEmailIn(List<String> emails);

    @Query("SELECT c.email FROM Contact c WHERE c.organizationId = :orgId")
    Set<String> findAllEmailsByOrganizationId(@Param("orgId") Long orgId);

    Optional<Contact> findByUnsubscribeToken(String token);

    @Query("SELECT c FROM Contact c WHERE c.status = 'ACTIVE' AND c.organizationId = :orgId ORDER BY c.engagementScore DESC, c.lastOpenedAt DESC")
    List<Contact> findMostEngagedByOrganizationId(@Param("orgId") Long orgId, Pageable pageable);
}
