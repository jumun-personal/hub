package com.jumunhasyeo.stock.infrastructure.inbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InboxRepositoryAdapter implements InboxRepository {
    private final JpaInboxRepository jpaInboxRepository;

    @Override
    public void save(InboxEvent inboxEvent) {
        jpaInboxRepository.save(inboxEvent);
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
