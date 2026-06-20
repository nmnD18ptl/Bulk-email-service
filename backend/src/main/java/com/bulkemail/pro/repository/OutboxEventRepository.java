package com.bulkemail.pro.repository;

import com.bulkemail.pro.model.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Relay poller fetches the oldest pending events first (FIFO). */
    @Query("""
        SELECT e FROM OutboxEvent e
        WHERE e.status = 'PENDING'
        ORDER BY e.createdAt ASC
        LIMIT 100
        """)
    List<OutboxEvent> findPendingBatch();

    /** Retry events that failed fewer than 3 times. */
    @Query("""
        SELECT e FROM OutboxEvent e
        WHERE e.status = 'FAILED' AND e.retryCount < 3
        ORDER BY e.createdAt ASC
        LIMIT 50
        """)
    List<OutboxEvent> findRetryableFailed();
}
