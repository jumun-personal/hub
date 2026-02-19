package com.jumunhasyeo.hub.hubRoute.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HubRouteBuildFailedEvent extends HubRouteDomainEvent {
    private UUID hubId;
    private String reason;
}
