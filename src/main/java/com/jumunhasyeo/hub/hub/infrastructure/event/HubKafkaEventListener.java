package com.jumunhasyeo.hub.hub.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.util.KafkaUtil;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubUpdatedEvent;
import com.jumunhasyeo.hub.hub.application.HubCreationSagaService;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildCompletedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import static com.jumunhasyeo.hub.hub.infrastructure.event.ListenEventRegistry.*;


@Slf4j
@Component
@RequiredArgsConstructor
public class HubKafkaEventListener {

    private final ObjectMapper objectMapper;
    private final HubCreationSagaService hubCreationSagaService;

    @KafkaListener(
            topics = "${spring.kafka.topics.hub}",
            groupId = "${spring.kafka.consumer.hub}",
            containerFactory = "kafkaListenerContainerFactory"
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
            objectMapper.readValue(payload, HubCreatedEvent.class);
        } else if (simpleClassName.equals(HUB_DELETED_EVENT.getEventName())) {
            objectMapper.readValue(payload, HubDeletedEvent.class);
        } else if (simpleClassName.equals(HUB_UPDATE_EVENT.getEventName())) {
            objectMapper.readValue(payload, HubUpdatedEvent.class);
        } else if (simpleClassName.equals(HUB_ROUTE_BUILD_COMPLETED_EVENT.getEventName())) {
            HubRouteBuildCompletedEvent event = objectMapper.readValue(payload, HubRouteBuildCompletedEvent.class);
            hubCreationSagaService.complete(event.getHubId());
        } else if (simpleClassName.equals(HUB_ROUTE_BUILD_FAILED_EVENT.getEventName())) {
            HubRouteBuildFailedEvent event = objectMapper.readValue(payload, HubRouteBuildFailedEvent.class);
            hubCreationSagaService.compensate(event.getHubId(), event.getReason());
        } else {
            log.info("Unhandled event type: {}", simpleClassName);
            log.info("Unhandled event payload: {}", payload);
        }
    }
}
