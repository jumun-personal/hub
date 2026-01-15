package com.jumunhasyeo.hub.hub.domain.event;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxMessage;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.UUID;

@Getter
@Schema(description = "HubNameUpdatedEvent")
public class HubNameUpdatedEvent extends HubDomainEvent implements OutboxMessage {
    @Schema(description = "허브Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private final UUID hubId;
    @Schema(description = "허브 이름", example = "송파B")
    private final String name;

    public HubNameUpdatedEvent(UUID hubId, String name) {
        this.hubId = hubId;
        this.name = name;
    }

    public static HubNameUpdatedEvent of(Hub hub) {
        return new HubNameUpdatedEvent(hub.getHubId(), hub.getName());
    }

    @Override
    public String eventName() {
        return HubNameUpdatedEvent.class.getSimpleName();
    }

    @Override
    public String eventKey() {
        return getEventKey();
    }
}
