package com.jumunhasyeo.hub.hubRoute.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.stereotype.Component;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubRouteDlqRecoverer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public DefaultErrorHandler buildErrorHandler() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(2);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(2000L);

        DeadLetterPublishingRecoverer dlqRecoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition())
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(dlqRecoverer, backOff);
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class);
        return errorHandler;
    }
}
