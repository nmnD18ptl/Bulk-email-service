package com.bulkemail.pro.repository;

import com.bulkemail.pro.model.entity.EmailTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailTrackingRepository extends JpaRepository<EmailTracking, Long> {

    Optional<EmailTracking> findByTrackingId(String trackingId);

    List<EmailTracking> findByCampaignIdAndEventType(Long campaignId, EmailTracking.TrackingEvent eventType);

    @Query("SELECT COUNT(DISTINCT t.contact.id) FROM EmailTracking t WHERE t.campaign.id = :campaignId AND t.eventType = :eventType")
    long countUniqueEvents(@Param("campaignId") Long campaignId, @Param("eventType") EmailTracking.TrackingEvent eventType);

    boolean existsByTrackingIdAndEventType(String trackingId, EmailTracking.TrackingEvent eventType);

    @Query("SELECT t.originalUrl, COUNT(t) FROM EmailTracking t WHERE t.campaign.id = :campaignId AND t.eventType = 'CLICKED' GROUP BY t.originalUrl ORDER BY COUNT(t) DESC")
    List<Object[]> findTopClickedLinks(@Param("campaignId") Long campaignId);
}
