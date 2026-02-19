package com.jumunhasyeo.hub.hubRoute.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum PublishEventRegistry {
    HUB_ROUTE_CREATED_EVENT(HubRouteCreatedEvent.class.getSimpleName()),
    HUB_ROUTE_DELETED_EVENT(HubRouteDeletedEvent.class.getSimpleName()),
    HUB_ROUTE_BUILD_REQUESTED_EVENT(HubRouteBuildRequestedEvent.class.getSimpleName()),
    HUB_ROUTE_BUILD_FAILED_EVENT(HubRouteBuildFailedEvent.class.getSimpleName()),
    HUB_ROUTE_BUILD_COMPLETED_EVENT(HubRouteBuildCompletedEvent.class.getSimpleName());

    private final String eventName;

    public static String of(String simpleName) {
        for (PublishEventRegistry registry : values()) {
            if (registry.getEventName().equals(simpleName)) {
                return registry.getEventName();
            }
        }
        throw new IllegalArgumentException("No matching event found for simple name: " + simpleName);
    }
}
