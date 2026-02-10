package com.jumunhasyeo.hub.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface JpaOutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    // PENDING 상태인 이벤트 상위 100개 조회
    List<OutboxEvent> findTop100ByStatusOrderByIdAsc(OutboxStatus status);
    List<OutboxEvent> findTop100ByStatusAndCreatedAtBeforeOrderByIdAsc(OutboxStatus status, LocalDateTime createdAt);

    @Query(value = """
            SELECT *
            FROM p_outbox_events
            WHERE status = :pendingStatus
               OR status = :failedStatus
               OR (status = :processingStatus AND claimed_at < :staleBefore)
            ORDER BY created_at ASC, id ASC
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findTop100ClaimableForUpdateSkipLocked(
            @Param("pendingStatus") String pendingStatus,
            @Param("failedStatus") String failedStatus,
            @Param("processingStatus") String processingStatus,
            @Param("staleBefore") LocalDateTime staleBefore
    );

    @Query(value = """
            SELECT *
            FROM p_outbox_events
            WHERE event_key = :eventKey
              AND (
                    status = :pendingStatus
                 OR status = :failedStatus
                 OR (status = :processingStatus AND claimed_at < :staleBefore)
              )
            ORDER BY created_at ASC, id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OutboxEvent> findClaimableByEventKeyForUpdateSkipLocked(
            @Param("eventKey") String eventKey,
            @Param("pendingStatus") String pendingStatus,
            @Param("failedStatus") String failedStatus,
            @Param("processingStatus") String processingStatus,
            @Param("staleBefore") LocalDateTime staleBefore
    );

    @Modifying
    @Transactional
    int deleteByStatusAndCreatedAtBefore(OutboxStatus outboxStatus, LocalDateTime localDateTime);

    Optional<OutboxEvent> findByEventKey(String eventKey);
}
