package com.jumunhasyeo.hub.hubRoute.infrastructure.event;

import com.jumunhasyeo.hub.hubRoute.application.HubRouteEventPublisher;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildCompletedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildFailedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildRequestedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteCreatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HubRouteSpringEventPublisher implements HubRouteEventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishRouteCreatedEvent(List<HubRouteCreatedEvent> eventList) {
        for (HubRouteCreatedEvent event : eventList) {
            applicationEventPublisher.publishEvent(event);
        }
    }

    @Override
    public void publishRouteDeletedEvent(List<HubRouteDeletedEvent> eventList) {
        for (HubRouteDeletedEvent event : eventList) {
            applicationEventPublisher.publishEvent(event);
        }
    }

    @Override
    public void publishRouteBuildRequested(List<HubRouteBuildRequestedEvent> eventList) {
        for (HubRouteBuildRequestedEvent event : eventList) {
            applicationEventPublisher.publishEvent(event);
        }
    }

    @Override
    public void publishRouteBuildFailed(UUID hubId, String reason) {
        applicationEventPublisher.publishEvent(new HubRouteBuildFailedEvent(hubId, reason));
    }

    @Override
    public void publishRouteBuildCompleted(BuildRouteCommand command) {
        applicationEventPublisher.publishEvent(HubRouteBuildCompletedEvent.from(command));
    }
}
