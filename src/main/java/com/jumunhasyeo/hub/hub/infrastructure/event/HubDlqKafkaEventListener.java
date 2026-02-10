package com.jumunhasyeo.hub.hub.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.util.KafkaUtil;
import com.jumunhasyeo.hub.hub.application.HubCreationSagaService;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import static com.jumunhasyeo.hub.hub.infrastructure.event.ListenEventRegistry.HUB_CREATED_EVENT;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubDlqKafkaEventListener {

    private static final String UNKNOWN_FAILURE_REASON = "Hub route build failed and moved to DLQ";

    private final ObjectMapper objectMapper;
    private final HubCreationSagaService hubCreationSagaService;

    @KafkaListener(
            topics = "${spring.kafka.topics.hub}.DLQ",
            groupId = "${spring.kafka.consumer.hub}-dlq",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void listen(
            @Payload String event,
            @Header(name = "eventType", required = false) String fullTypeName,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage
    ) throws JsonProcessingException {
        if (fullTypeName == null) {
            return;
        }

        String simpleClassName = KafkaUtil.getClassName(fullTypeName);
        if (simpleClassName == null || simpleClassName.isBlank()) {
            return;
        }
        dispatch(event, simpleClassName, exceptionMessage);
    }

    public void dispatch(String payload, String simpleClassName, String exceptionMessage) throws JsonProcessingException {
        if (!simpleClassName.equals(HUB_CREATED_EVENT.getEventName())) {
            return;
        }

        HubCreatedEvent event = objectMapper.readValue(payload, HubCreatedEvent.class);
        hubCreationSagaService.compensate(event.getHubId(), resolveReason(exceptionMessage));
    }

    private String resolveReason(String exceptionMessage) {
        if (exceptionMessage == null || exceptionMessage.isBlank()) {
            return UNKNOWN_FAILURE_REASON;
        }
        return exceptionMessage;
    }
}
