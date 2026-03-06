package com.jumunhasyeo.hub.hub.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.util.KafkaUtil;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import static com.jumunhasyeo.hub.hub.infrastructure.event.ListenEventRegistry.*;


@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hub.api.events.enabled", havingValue = "true", matchIfMissing = true)
public class HubKafkaEventListener {

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${spring.kafka.topics.hub}",
            groupId = "${spring.kafka.consumer.hub}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void listen(
            @Payload String event,
            @Header(name = "eventType", required = false) String fullTypeName
    ) throws JsonProcessingException {
        if (fullTypeName == null) {
            return;
        }

        String simpleClassName = KafkaUtil.getClassName(fullTypeName);
        if (simpleClassName.isBlank()) {
            return;
        }
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
            log.debug("Skip legacy hub-route build completed event.");
        } else if (simpleClassName.equals(HUB_ROUTE_BUILD_FAILED_EVENT.getEventName())) {
            log.debug("Skip legacy hub-route build failed event.");
        } else {
            return;
        }
    }
}
