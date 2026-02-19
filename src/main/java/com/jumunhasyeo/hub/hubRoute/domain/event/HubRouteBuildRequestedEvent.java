package com.jumunhasyeo.hub.hubRoute.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HubRouteBuildRequestedEvent extends HubRouteDomainEvent {
    private UUID hubId;
    private List<UUID> routeIds;
}
