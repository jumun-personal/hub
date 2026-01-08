package com.jumunhasyeo.common.outbox;

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

    public void dispatch(OutboxEvent event) {
        if(event.getTopic().equals(hubTopic)) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(hubTopic, event.getPayload());
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
}
