package com.jumunhasyeo.hub.infrastructure.outbox;

public interface OutboxMessage {
    String eventName();
    String eventKey();
}
