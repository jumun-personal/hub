package com.jumunhasyeo.hub.hubRoute.infrastructure.event;

import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HubRouteEventHandler {

    public void hubDeleted(HubDeletedEvent event) {
        log.debug("Skip HubDeletedEvent for route worker. hubId={}", event.getHubId());
    }

    public void hubCreated(HubCreatedEvent event) {
        log.debug("Skip HubCreatedEvent for route worker. hubId={}", event.getHubId());
    }

}
