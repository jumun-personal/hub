package com.jumunhasyeo.hub.hubRoute.application;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxService;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubRouteSpringEventListener {

    private final OutboxService outboxService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleBeforeCommit(HubRouteDomainEvent event) {
        log.info("sync {} received key={}", event.eventName(), event.eventKey());
        outboxService.save(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAfterCommit(HubRouteDomainEvent event) {
        log.info("afterCommit {} received key={}", event.eventName(), event.eventKey());
        outboxService.publishAfterCommit(event.eventKey());
    }
}
