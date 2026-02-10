package com.jumunhasyeo.hub.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OutboxClaimService {

    private final OutboxRepository outboxRepository;

    @Transactional
    public Optional<OutboxEvent> claimByEventKey(String eventKey, LocalDateTime staleBefore) {
        Optional<OutboxEvent> outboxEvent = outboxRepository.findClaimableByEventKeyForUpdateSkipLocked(
                eventKey,
                staleBefore
        );
        outboxEvent.ifPresent(event -> {
            event.claimProcessing();
            outboxRepository.save(event);
        });
        return outboxEvent;
    }

    @Transactional
    public List<OutboxEvent> claimPublishableEvents(LocalDateTime staleBefore) {
        List<OutboxEvent> events = outboxRepository.findTop100ClaimableForUpdateSkipLocked(staleBefore);
        for (OutboxEvent event : events) {
            event.claimProcessing();
            outboxRepository.save(event);
        }
        return events;
    }

    @Transactional
    public void markPublishSuccess(OutboxEvent event) {
        event.publishSuccess();
        outboxRepository.save(event);
    }

    @Transactional
    public void markPublishFailure(OutboxEvent event, String errorMessage) {
        event.publishFail(errorMessage);
        outboxRepository.save(event);
    }
}
