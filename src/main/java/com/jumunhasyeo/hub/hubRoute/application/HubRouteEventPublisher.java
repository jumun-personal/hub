package com.jumunhasyeo.hub.hubRoute.application;

import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteCreatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDeletedEvent;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;

import java.util.List;

public interface HubRouteEventPublisher {
    void publishRouteCreatedEvent(List<HubRouteCreatedEvent> eventList);
    void publishRouteDeletedEvent(List<HubRouteDeletedEvent> eventList);
    void publishRouteBuildCompleted(BuildRouteCommand command);
    void publishRouteBuildFailed(BuildRouteCommand command, String reason);
}
