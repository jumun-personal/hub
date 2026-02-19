package com.jumunhasyeo.hub.hubRoute.infrastructure.event;

import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubNameUpdatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildCompletedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildRequestedEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ListenEventRegistry {

    HUB_CREATED_EVENT(HubCreatedEvent.class.getSimpleName()),
    HUB_DELETED_EVENT(HubDeletedEvent.class.getSimpleName()),
    HUB_NAME_UPDATE_EVENT(HubNameUpdatedEvent.class.getSimpleName()),
    HUB_ROUTE_BUILD_REQUESTED_EVENT(HubRouteBuildRequestedEvent.class.getSimpleName()),
    HUB_ROUTE_BUILD_COMPLETED_EVENT(HubRouteBuildCompletedEvent.class.getSimpleName());

    private final String eventName;
}
