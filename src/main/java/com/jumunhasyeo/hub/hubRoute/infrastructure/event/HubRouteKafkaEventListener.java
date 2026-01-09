package com.jumunhasyeo.hub.hubRoute.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.util.KafkaUtil;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import static com.jumunhasyeo.hub.hubRoute.infrastructure.event.ListenEventRegistry.HUB_CREATED_EVENT;
import static com.jumunhasyeo.hub.hubRoute.infrastructure.event.ListenEventRegistry.HUB_DELETED_EVENT;
import static com.jumunhasyeo.hub.hubRoute.infrastructure.event.ListenEventRegistry.HUB_ROUTE_BUILD_COMPLETED_EVENT;
import static com.jumunhasyeo.hub.hubRoute.infrastructure.event.ListenEventRegistry.HUB_ROUTE_BUILD_FAILED_EVENT;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubRouteKafkaEventListener {

    private final HubRouteEventHandler hubRouteEventHandler;
    private final ObjectMapper objectMapper;
    @KafkaListener(
            topics = "${spring.kafka.topics.hub}",
            groupId = "${spring.kafka.consumer.hub-route}",
            containerFactory = "hubRouteKafkaListenerContainerFactory"
    )
    public void listen(
            @Payload String event,
            @Header(name = "eventType", required = false) String fullTypeName
    ) throws JsonProcessingException {
        String simpleClassName = KafkaUtil.getClassName(fullTypeName);
        dispatch(event, simpleClassName);
    }

    public void dispatch(String payload, String simpleClassName) throws JsonProcessingException {
        if (simpleClassName.equals(HUB_CREATED_EVENT.getEventName())) {
            HubCreatedEvent hubCreatedEvent = objectMapper.readValue(payload, HubCreatedEvent.class);
            hubRouteEventHandler.hubCreated(hubCreatedEvent);

        } else if (simpleClassName.equals(HUB_DELETED_EVENT.getEventName())) {
            HubDeletedEvent hubDeletedEvent = objectMapper.readValue(payload, HubDeletedEvent.class);
            hubRouteEventHandler.hubDeleted(hubDeletedEvent);

        } else if (simpleClassName.equals(HUB_ROUTE_BUILD_COMPLETED_EVENT.getEventName())
                || simpleClassName.equals(HUB_ROUTE_BUILD_FAILED_EVENT.getEventName())) {
            log.debug("Skip hub-route build result event: {}", simpleClassName);
        } else {
            log.info("Unhandled event type: {}", simpleClassName);
            log.info("Unhandled event payload: {}", payload);
        }
    }
}
