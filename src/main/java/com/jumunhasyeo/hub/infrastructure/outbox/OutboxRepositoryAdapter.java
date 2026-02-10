package com.jumunhasyeo.hub.infrastructure.outbox;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OutboxRepositoryAdapter implements OutboxRepository{
    private final JpaOutboxRepository jpaOutboxRepository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return jpaOutboxRepository.save(outboxEvent);
    }

    @Override
    public List<OutboxEvent> findTop100ByStatusOrderByIdAsc(OutboxStatus status) {
        return jpaOutboxRepository.findTop100ByStatusOrderByIdAsc(status);
    }

    @Override
    public List<OutboxEvent> findTop100ByStatusAndCreatedAtBeforeOrderByIdAsc(OutboxStatus status, LocalDateTime createdAt) {
        return jpaOutboxRepository.findTop100ByStatusAndCreatedAtBeforeOrderByIdAsc(status, createdAt);
    }

    @Override
    public List<OutboxEvent> findTop100ClaimableForUpdateSkipLocked(LocalDateTime staleBefore) {
        return jpaOutboxRepository.findTop100ClaimableForUpdateSkipLocked(
                OutboxStatus.PENDING.name(),
                OutboxStatus.FAILED.name(),
                OutboxStatus.PROCESSING.name(),
                staleBefore
        );
    }

    @Override
    public Optional<OutboxEvent> findClaimableByEventKeyForUpdateSkipLocked(String eventKey, LocalDateTime staleBefore) {
        return jpaOutboxRepository.findClaimableByEventKeyForUpdateSkipLocked(
                eventKey,
                OutboxStatus.PENDING.name(),
                OutboxStatus.FAILED.name(),
                OutboxStatus.PROCESSING.name(),
                staleBefore
        );
    }

    @Override
    public int deleteByStatusAndCreatedAtBefore(OutboxStatus outboxStatus, LocalDateTime localDateTime) {
        return jpaOutboxRepository.deleteByStatusAndCreatedAtBefore(outboxStatus, localDateTime);
    }

    @Override
    public OutboxEvent findByEventKey(String eventKey) {
        return jpaOutboxRepository.findByEventKey(eventKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_FOUND));
    }
}
