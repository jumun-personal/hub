package com.jumunhasyeo.hub.infrastructure.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class OutboxDispatcher {
    @Value("${spring.kafka.topics.hub}")
    private String hubTopic;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void dispatch(OutboxEvent event) {
        if(event.getTopic().equals(hubTopic)) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        hubTopic,
                        resolvePartitionKey(event),
                        event.getPayload()
                );
                record.headers().add("eventType", event.getEventName().getBytes());
                record.headers().add("source", "hub-service".getBytes());

                kafkaTemplate.send(record).get(); // 동기 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Kafka 이벤트 발행에 실패했습니다.", e);
            } catch (ExecutionException e) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Kafka 이벤트 발행에 실패했습니다.", e);
            }
        }
        else{
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 토픽입니다. topic=" + event.getTopic());
        }
    }

    private String resolvePartitionKey(OutboxEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            JsonNode hubId = payload.get("hubId");
            if (hubId != null && !hubId.isNull()) {
                return hubId.asText();
            }
            JsonNode startHub = payload.get("startHub");
            if (startHub != null && !startHub.isNull()) {
                return startHub.asText();
            }
            return event.getEventKey();
        } catch (Exception e) {
            return event.getEventKey();
        }
    }
}
