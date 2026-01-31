package com.jumunhasyeo.stock.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.jumunhasyeo.stock.infrastructure.event.ListenEventRegistry.ORDER_CANCEL_EVENT;
import static com.jumunhasyeo.stock.infrastructure.event.ListenEventRegistry.ORDER_ROLLED_BACK_EVENT;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaStockEventListener {
    private final OrderCompensateHandler orderCompensateHandler;
    private final ObjectMapper objectMapper;
    private final OrderAclService orderAclService;

    @KafkaListener(
            topics = "${spring.kafka.topics.order}",
            groupId = "${spring.kafka.consumer.stock}",
            containerFactory = "stockKafkaListenerContainerFactory"
    )
    public void listen(
            @Payload String payload,
            @Header(name = "eventType", required = false) String eventType
    ) throws JsonProcessingException {
        log.info("Received event. EventType: {}, Payload: {}", eventType, payload);
        Optional<String> mappedClassName = orderAclService.convert(eventType);
        if (mappedClassName.isEmpty()) {
            if (eventType == null) {
                log.warn("Skip stock event due to missing eventType header");
            } else {
                log.warn("Skip stock event due to unsupported eventType={}", eventType);
            }
            return;
        }
        dispatch(payload, mappedClassName.get());
    }

    public void dispatch(String payload, String simpleClassName) throws JsonProcessingException {
        if (simpleClassName.equals(ORDER_CANCEL_EVENT.getEventName())) {
            OrderCancelEvent orderCancelEvent = objectMapper.readValue(payload, OrderCancelEvent.class);
            orderCompensateHandler.compensate(orderCancelEvent);

        } else if (simpleClassName.equals(ORDER_ROLLED_BACK_EVENT.getEventName())) {
            OrderRolledBackEvent orderRolledBackEvent = objectMapper.readValue(payload, OrderRolledBackEvent.class);
            orderCompensateHandler.compensate(orderRolledBackEvent);

        } else {
            log.warn("Unhandled event type: {}", simpleClassName);
        }
    }
}
