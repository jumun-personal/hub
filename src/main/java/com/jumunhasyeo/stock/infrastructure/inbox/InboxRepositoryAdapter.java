package com.jumunhasyeo.stock.infrastructure.inbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class InboxRepositoryAdapter implements InboxRepository {
    private final JpaInboxRepository jpaInboxRepository;

    @Override
    public void save(InboxEvent inboxEvent) {
        jpaInboxRepository.save(inboxEvent);
    }

    @Override
    @Transactional
    public boolean saveIfAbsent(InboxEvent inboxEvent) {
        return jpaInboxRepository.insertIgnore(
                UUID.randomUUID(),
                inboxEvent.getEventKey(),
                inboxEvent.getEventName(),
                inboxEvent.getPayload(),
                inboxEvent.getStatus().name(),
                inboxEvent.getRetryCount(),
                inboxEvent.getMaxRetries(),
                inboxEvent.getReceivedAt(),
                inboxEvent.getProcessedAt(),
                inboxEvent.getErrorMessage()
        ) > 0;
    }

    @Override
    public List<InboxEvent> findByStatusAndModifiedAtBefore(InboxStatus inboxStatus, LocalDateTime threshold) {
        return jpaInboxRepository.findByStatusAndModifiedAtBefore(inboxStatus, threshold);
    }

    public Optional<InboxEvent> findByEventKey(String eventKey) {
        return jpaInboxRepository.findByEventKey(eventKey);
    }
    
    @Override
    public boolean existsByEventKey(String eventKey) {
        return jpaInboxRepository.existsByEventKey(eventKey);
    }
}
