package com.jumunhasyeo.hub.hubRoute.infrastructure.event;

import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.application.service.RoutePairBuildProcessor;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class HubRouteEventHandler {
    private final HubRouteService hubRouteService;
    private final RoutePairBuildProcessor routePairBuildProcessor;

    public void hubDeleted(HubDeletedEvent event) {
        hubRouteService.deleteRoutesForHub(event.getHubId(), event.getDeletedBy());
    }

    public void hubCreated(HubCreatedEvent event) {
        BuildRouteCommand command = BuildRouteCommand.from(event);
        hubRouteService.buildRoutesForNewHub(command);
    }

    public void routeBuildRequested(HubRouteBuildRequestedEvent event) {
        routePairBuildProcessor.process(event.getRouteIds());
    }
}
