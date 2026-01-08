package com.jumunhasyeo.hub.hubRoute.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDomainEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.PublishEventRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class KafkaHubRouteEventPublisher {
    private final KafkaTemplate<String, String> template;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topics.hub}")
    private String hubTopic;

    public CompletableFuture<SendResult<String, String>> publish(HubRouteDomainEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(hubTopic, json);
            record.headers().add("eventType", PublishEventRegistry.of(event.getClass().getSimpleName()).getBytes());
            record.headers().add("source", "hub-service".getBytes());
            return template.send(record);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "허브 경로 이벤트 직렬화에 실패했습니다.", e);
        }
    }
}
