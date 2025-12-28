package com.jumunhasyeo.hub.hubRoute.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PublishEventRegistryTest {

    @Test
    @DisplayName("of 메서드는 유효한 이벤트 이름에 대해 해당 이벤트 이름을 반환한다.")
    public void of_validEventName_returnsEventName() {
        String eventName = PublishEventRegistry.of(HubRouteCreatedEvent.class.getSimpleName());
        assertEquals(HubRouteCreatedEvent.class.getSimpleName(), eventName);
    }

    @Test
    @DisplayName("of 메서드는 유효하지 않은 이벤트 이름에 대해 예외를 던진다.")
    void of_invalidEventName_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> PublishEventRegistry.of("UnknownEvent"));
    }
}
