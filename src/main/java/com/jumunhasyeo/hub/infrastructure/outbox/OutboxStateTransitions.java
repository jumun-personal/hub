package com.jumunhasyeo.hub.infrastructure.outbox;

import com.jumunhasyeo.hub.hub.application.HubCreationOutboxFailureHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class OutboxStateTransitions {

    private final JpaOutboxRepository outboxRepository;
    private final HubCreationOutboxFailureHandler terminalFailureHandler;

    @Transactional
    public Optional<OutboxEvent> claimByEventKey(String eventKey, LocalDateTime staleBefore) {
        Optional<OutboxEvent> outboxEvent = outboxRepository.findClaimableByEventKeyForUpdateSkipLocked(
                eventKey,
                OutboxStatus.PENDING.name(),
                OutboxStatus.FAILED.name(),
                OutboxStatus.PROCESSING.name(),
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
        List<OutboxEvent> events = outboxRepository.findTop100ClaimableForUpdateSkipLocked(
                OutboxStatus.PENDING.name(),
                OutboxStatus.FAILED.name(),
                OutboxStatus.PROCESSING.name(),
                staleBefore
        );
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
        if (event.getStatus() == OutboxStatus.DEAD) {
            terminalFailureHandler.handle(event);
        }
    }

    @Transactional
    public void markPublishDead(OutboxEvent event, String errorMessage) {
        event.markDead(errorMessage);
        outboxRepository.save(event);
        terminalFailureHandler.handle(event);
    }
}
