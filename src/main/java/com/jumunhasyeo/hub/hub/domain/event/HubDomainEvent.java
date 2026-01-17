package com.jumunhasyeo.hub.hub.domain.event;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxMessage;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public abstract class HubDomainEvent implements OutboxMessage {
    private final LocalDateTime occurredAt;
    private final String eventKey;

    public HubDomainEvent() {
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
