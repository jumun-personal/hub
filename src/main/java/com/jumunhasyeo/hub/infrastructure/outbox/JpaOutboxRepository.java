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
            WHERE status = :status
            ORDER BY id ASC
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findTop100ByStatusForUpdateSkipLocked(@Param("status") String status);

    @Query(value = """
            SELECT *
            FROM p_outbox_events
            WHERE status = :status
              AND created_at < :createdAt
            ORDER BY id ASC
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findTop100ByStatusAndCreatedAtBeforeForUpdateSkipLocked(
            @Param("status") String status,
            @Param("createdAt") LocalDateTime createdAt
    );

    @Modifying
    @Transactional
    int deleteByStatusAndCreatedAtBefore(OutboxStatus outboxStatus, LocalDateTime localDateTime);

    Optional<OutboxEvent> findByEventKey(String eventKey);
}
