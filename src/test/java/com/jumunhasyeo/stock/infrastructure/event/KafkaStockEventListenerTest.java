package com.jumunhasyeo.stock.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class KafkaStockEventListenerTest {

    @Mock
    private OrderCompensateHandler orderCompensateHandler;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OrderAclService orderAclService;

    @InjectMocks
    private KafkaStockEventListener kafkaStockEventListener;

    @Test
    @DisplayName("OrderCancelEvent를 처리할 수 있다.")
    void dispatch_OrderCancelEvent_success() throws Exception {
        //given
        String payload = "{\"orderId\":\"123\"}";
        String simpleClassName = "OrderCancelEvent";
        OrderCancelEvent event = new OrderCancelEvent(UUID.randomUUID(), "", LocalDateTime.now());
        given(objectMapper.readValue(payload, OrderCancelEvent.class)).willReturn(event);

        //when
        kafkaStockEventListener.dispatch(payload, simpleClassName);

        //then
        then(orderCompensateHandler).should().compensate(event);
    }

    @Test
    @DisplayName("OrderRolledBackEvent를 처리할 수 있다.")
    void dispatch_OrderRolledBackEvent_success() throws Exception {
        //given
        String payload = "{\"orderId\":\"123\"}";
        String simpleClassName = "OrderRolledBackEvent";
        OrderRolledBackEvent event = new OrderRolledBackEvent(UUID.randomUUID(), "", LocalDateTime.now());
        given(objectMapper.readValue(payload, OrderRolledBackEvent.class)).willReturn(event);

        //when
        kafkaStockEventListener.dispatch(payload, simpleClassName);

        //then
        then(orderCompensateHandler).should().compensate(event);
    }

    @Test
    @DisplayName("처리할 수 없는 이벤트 타입은 무시된다.")
    void dispatch_UnknownEventType_ignored() throws Exception {
        //given
        String payload = "{\"data\":\"test\"}";
        String simpleClassName = "UnknownEvent";

        //when
        kafkaStockEventListener.dispatch(payload, simpleClassName);

        //then
        then(orderCompensateHandler).should(never()).compensate(any(OrderCancelEvent.class));
        then(orderCompensateHandler).should(never()).compensate(any(OrderRolledBackEvent.class));
    }

    @Test
    @DisplayName("eventType 헤더가 null이면 이벤트를 건너뛴다.")
    void listen_WhenEventTypeNull_skip() throws Exception {
        String payload = "{\"orderId\":\"123\"}";
        given(orderAclService.convert(null)).willReturn(Optional.empty());

        kafkaStockEventListener.listen(payload, null);

        then(orderAclService).should().convert(null);
        then(orderCompensateHandler).should(never()).compensate(any(OrderCancelEvent.class));
        then(orderCompensateHandler).should(never()).compensate(any(OrderRolledBackEvent.class));
    }

    @Test
    @DisplayName("미지원 eventType이면 이벤트를 건너뛴다.")
    void listen_WhenEventTypeUnsupported_skip() throws Exception {
        String payload = "{\"orderId\":\"123\"}";
        given(orderAclService.convert("UNKNOWN")).willReturn(Optional.empty());

        kafkaStockEventListener.listen(payload, "UNKNOWN");

        then(orderAclService).should().convert("UNKNOWN");
        then(orderCompensateHandler).should(never()).compensate(any(OrderCancelEvent.class));
        then(orderCompensateHandler).should(never()).compensate(any(OrderRolledBackEvent.class));
    }
}
