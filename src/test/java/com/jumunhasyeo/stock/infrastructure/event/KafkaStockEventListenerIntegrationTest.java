package com.jumunhasyeo.stock.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.testsupport.AbstractEventDispatchIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

public class KafkaStockEventListenerIntegrationTest extends AbstractEventDispatchIntegrationTest {

    @Autowired
    private KafkaStockEventListener kafkaStockEventListener;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("OrderCancelEvent를 수신하고 처리할 수 있다.")
    void dispatch_OrderCancelEvent_integration_success() throws Exception {
        //given
        OrderCancelEvent event = new OrderCancelEvent(UUID.randomUUID(), "", LocalDateTime.now());
        String payload = objectMapper.writeValueAsString(event);
        String simpleClassName = "OrderCancelEvent";

        //when
        kafkaStockEventListener.dispatch(payload, simpleClassName);

        //then
        then(orderCompensateHandler).should().compensate(any(OrderCancelEvent.class));
    }

    @Test
    @DisplayName("OrderRolledBackEvent를 수신하고 처리할 수 있다.")
    void dispatch_OrderRolledBackEvent_integration_success() throws Exception {
        //given
        OrderRolledBackEvent event = new OrderRolledBackEvent(UUID.randomUUID(), "", LocalDateTime.now());
        String payload = objectMapper.writeValueAsString(event);
        String simpleClassName = "OrderRolledBackEvent";

        //when
        kafkaStockEventListener.dispatch(payload, simpleClassName);

        //then
        then(orderCompensateHandler).should().compensate(any(OrderRolledBackEvent.class));
    }

    @Test
    @DisplayName("eventType이 null이면 이벤트를 건너뛴다.")
    void listen_WhenEventTypeNull_skip() throws Exception {
        String payload = "{\"data\":\"test\"}";

        kafkaStockEventListener.listen(payload, null);

        then(orderCompensateHandler).should(never()).compensate(any(OrderCancelEvent.class));
        then(orderCompensateHandler).should(never()).compensate(any(OrderRolledBackEvent.class));
    }
}
