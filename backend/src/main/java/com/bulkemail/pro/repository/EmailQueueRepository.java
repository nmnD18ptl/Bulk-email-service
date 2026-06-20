package com.bulkemail.pro.repository;

import com.bulkemail.pro.model.entity.EmailQueue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailQueueRepository extends JpaRepository<EmailQueue, Long> {

    List<EmailQueue> findByCampaignIdAndStatus(Long campaignId, EmailQueue.QueueStatus status);

    @Query("SELECT q FROM EmailQueue q JOIN FETCH q.campaign c LEFT JOIN FETCH c.smtpConfig JOIN FETCH q.contact WHERE q.campaign.id = :campaignId AND q.status = 'PENDING' ORDER BY q.priority ASC")
    List<EmailQueue> findPendingByCampaign(@Param("campaignId") Long campaignId, Pageable pageable);

    long countByCampaignIdAndStatus(Long campaignId, EmailQueue.QueueStatus status);

    Optional<EmailQueue> findByTrackingId(String trackingId);

    @Modifying
    @Query("UPDATE EmailQueue q SET q.status = 'CANCELLED' WHERE q.campaign.id = :campaignId AND q.status = 'PENDING'")
    int cancelPendingByCampaign(@Param("campaignId") Long campaignId);

    @Query("SELECT q FROM EmailQueue q WHERE q.status = 'FAILED' AND q.retryCount < q.maxRetries AND q.campaign.id = :campaignId")
    List<EmailQueue> findRetryable(@Param("campaignId") Long campaignId);

    @Query("SELECT COUNT(q) FROM EmailQueue q WHERE q.campaign.id = :campaignId AND q.status IN ('SENT', 'FAILED', 'BOUNCED', 'CANCELLED', 'SKIPPED')")
    long countProcessed(@Param("campaignId") Long campaignId);
}
