package com.jumunhasyeo.hub.infrastructure.outbox;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxRepository {
    OutboxEvent save(OutboxEvent outboxEvent);
    List<OutboxEvent> findTop100ByStatusOrderByIdAsc(OutboxStatus status);
    List<OutboxEvent> findTop100ByStatusAndCreatedAtBeforeOrderByIdAsc(OutboxStatus status, LocalDateTime createdAt);
    List<OutboxEvent> findTop100ClaimableForUpdateSkipLocked(LocalDateTime staleBefore);
    Optional<OutboxEvent> findClaimableByEventKeyForUpdateSkipLocked(String eventKey, LocalDateTime staleBefore);
    int deleteByStatusAndCreatedAtBefore(OutboxStatus outboxStatus, LocalDateTime localDateTime);
    OutboxEvent findByEventKey(String eventKey);
}
