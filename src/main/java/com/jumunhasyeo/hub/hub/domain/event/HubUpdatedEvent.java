package com.jumunhasyeo.hub.hub.domain.event;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import lombok.Getter;

import java.util.UUID;

@Getter
public class HubUpdatedEvent extends HubDomainEvent {
    private final UUID hubId;

    public HubUpdatedEvent(UUID hubId) {
        this.hubId = hubId;
    }

    public static HubUpdatedEvent of(Hub hub) {
        return new HubUpdatedEvent(hub.getHubId());
    }

}
