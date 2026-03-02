package com.jumunhasyeo.hub.hub.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.infrastructure.outbox.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HubCreationOutboxFailureHandler {

    private final ObjectMapper objectMapper;
    private final HubCreationSagaService hubCreationSagaService;

    public void handle(OutboxEvent event) {
        if (!HubCreatedEvent.class.getSimpleName().equals(event.getEventName())) {
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            UUID hubId = UUID.fromString(payload.path("hubId").asText());
            hubCreationSagaService.compensate(hubId, "Hub creation event publication exhausted: " + event.getErrorMessage());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Hub 생성 Outbox 최종 실패를 처리하지 못했습니다.",
                    e
            );
        }
    }
}
