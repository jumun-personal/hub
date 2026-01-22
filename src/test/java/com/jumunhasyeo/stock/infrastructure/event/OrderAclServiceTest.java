package com.jumunhasyeo.stock.infrastructure.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAclServiceTest {

    private final OrderAclService orderAclService = new OrderAclService();

    @Test
    @DisplayName("ORDER_CANCELLED는 OrderCancelEvent로 매핑된다")
    void convert_orderCancelled() {
        Optional<String> result = orderAclService.convert("ORDER_CANCELLED");

        assertThat(result).hasValue(ListenEventRegistry.ORDER_CANCEL_EVENT.getEventName());
    }

    @Test
    @DisplayName("ORDER_ROLLEDBACK은 OrderRolledBackEvent로 매핑된다")
    void convert_orderRolledBack() {
        Optional<String> result = orderAclService.convert("ORDER_ROLLEDBACK");

        assertThat(result).hasValue(ListenEventRegistry.ORDER_ROLLED_BACK_EVENT.getEventName());
    }

    @Test
    @DisplayName("ORDER_CREATED는 OrderCreatedEvent로 매핑된다")
    void convert_orderCreated() {
        Optional<String> result = orderAclService.convert("ORDER_CREATED");

        assertThat(result).hasValue(ListenEventRegistry.ORDER_CREATED.getEventName());
    }

    @Test
    @DisplayName("eventType이 null이면 empty를 반환한다")
    void convert_null() {
        Optional<String> result = orderAclService.convert(null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("미지원 eventType이면 empty를 반환한다")
    void convert_unknown() {
        Optional<String> result = orderAclService.convert("UNKNOWN");

        assertThat(result).isEmpty();
    }
}
