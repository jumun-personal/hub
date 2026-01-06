package com.jumunhasyeo.hub.hub.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublishEventRegistryTest {

    @Test
    @DisplayName("of는 유효한 이벤트 이름을 반환한다.")
    void of_valid_event() {
        String eventName = PublishEventRegistry.of(HubCreatedEvent.class.getSimpleName());

        assertEquals(HubCreatedEvent.class.getSimpleName(), eventName);
    }

    @Test
    @DisplayName("of는 유효하지 않은 이벤트 이름에 대해 예외를 던진다.")
    void of_invalid_event_throws() {
        assertThrows(IllegalArgumentException.class, () -> PublishEventRegistry.of("UnknownEvent"));
    }
}
