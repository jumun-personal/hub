package com.jumunhasyeo.hub.hubRoute.domain.event;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class HubRouteDomainEvent implements OutboxMessage {
    private final LocalDateTime occurredAt;
    private final String eventKey;

    public HubRouteDomainEvent() {
        eventKey = UUID.randomUUID().toString();
        this.occurredAt = LocalDateTime.now();
    }

    public String getEventKey() {
        return occurredAt+"-"+eventKey;
    }

    @Override
    public String eventName() {
        return getClass().getSimpleName();
    }

    @Override
    public String eventKey() {
        return getEventKey();
    }
}
