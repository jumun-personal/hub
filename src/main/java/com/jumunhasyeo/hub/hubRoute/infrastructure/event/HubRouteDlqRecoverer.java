package com.jumunhasyeo.hub.hubRoute.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.jumunhasyeo.common.util.KafkaUtil;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hubRoute.application.HubRouteEventPublisher;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.stereotype.Component;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import static com.jumunhasyeo.hub.hubRoute.infrastructure.event.ListenEventRegistry.HUB_CREATED_EVENT;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubRouteDlqRecoverer {

    private static final String EVENT_TYPE_HEADER = "eventType";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final HubRouteEventPublisher hubRouteEventPublisher;

    public DefaultErrorHandler buildErrorHandler() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(2);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(2000L);

        DeadLetterPublishingRecoverer dlqRecoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition())
        );

        ConsumerRecordRecoverer recoverer = (record, ex) -> {
            maybePublishBuildFailed(record, ex);
            dlqRecoverer.accept(record, ex);
        };

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class);
        return errorHandler;
    }

    private void maybePublishBuildFailed(ConsumerRecord<?, ?> record, Exception ex) {
        String eventType = getHeader(record, EVENT_TYPE_HEADER);
        if (eventType == null) {
            return;
        }

        String simpleClassName = KafkaUtil.getClassName(eventType);
        if (!HUB_CREATED_EVENT.getEventName().equals(simpleClassName)) {
            return;
        }

        try {
            HubCreatedEvent hubCreatedEvent = objectMapper.readValue(
                    String.valueOf(record.value()),
                    HubCreatedEvent.class
            );
            BuildRouteCommand command = BuildRouteCommand.from(hubCreatedEvent);
            String reason = ex == null ? "Unknown error" : ex.getMessage();
            hubRouteEventPublisher.publishRouteBuildFailed(command, reason);
        } catch (Exception e) {
            log.error("Failed to publish HubRouteBuildFailedEvent in DLQ recoverer: {}", e.getMessage(), e);
        }
    }

    private String getHeader(ConsumerRecord<?, ?> record, String key) {
        if (record == null || record.headers() == null) {
            return null;
        }
        var header = record.headers().lastHeader(key);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value());
    }
}
