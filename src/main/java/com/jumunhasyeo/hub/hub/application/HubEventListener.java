package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxPublicationService;
import com.jumunhasyeo.hub.hub.domain.event.HubDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubEventListener {

    private final OutboxPublicationService outboxPublicationService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleBeforeCommit(HubDomainEvent event) {
        log.info("sync {} received key={}", event.eventName(), event.eventKey());
        outboxPublicationService.append(event);
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAfterCommit(HubDomainEvent event) {
        log.info("async {} received key={}", event.eventName(), event.eventKey());
        outboxPublicationService.publishAfterCommit(event.eventKey());
    }
}
