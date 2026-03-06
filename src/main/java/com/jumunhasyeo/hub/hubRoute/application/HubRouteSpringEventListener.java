package com.jumunhasyeo.hub.hubRoute.application;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxPublicationService;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hub.route.events.enabled", havingValue = "true", matchIfMissing = true)
public class HubRouteSpringEventListener {

    private final OutboxPublicationService outboxPublicationService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleBeforeCommit(HubRouteDomainEvent event) {
        log.info("sync {} received key={}", event.eventName(), event.eventKey());
        outboxPublicationService.append(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAfterCommit(HubRouteDomainEvent event) {
        log.info("afterCommit {} received key={}", event.eventName(), event.eventKey());
        outboxPublicationService.publishAfterCommit(event.eventKey());
    }
}
