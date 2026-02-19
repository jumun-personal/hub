package com.jumunhasyeo.hub.hubRoute.application;

import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteCreatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDeletedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildRequestedEvent;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;

import java.util.List;
import java.util.UUID;

public interface HubRouteEventPublisher {
    void publishRouteCreatedEvent(List<HubRouteCreatedEvent> eventList);
    void publishRouteDeletedEvent(List<HubRouteDeletedEvent> eventList);
    void publishRouteBuildRequested(List<HubRouteBuildRequestedEvent> eventList);
    void publishRouteBuildFailed(UUID hubId, String reason);
    void publishRouteBuildCompleted(BuildRouteCommand command);
}
