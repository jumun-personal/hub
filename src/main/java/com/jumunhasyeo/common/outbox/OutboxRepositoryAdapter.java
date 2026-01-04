package com.jumunhasyeo.common.outbox;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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
    public int deleteByStatusAndCreatedAtBefore(OutboxStatus outboxStatus, LocalDateTime localDateTime) {
        return jpaOutboxRepository.deleteByStatusAndCreatedAtBefore(outboxStatus, localDateTime);
    }

    @Override
    public OutboxEvent findByEventKey(String eventKey) {
        return jpaOutboxRepository.findByEventKey(eventKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_FOUND));
    }
}
