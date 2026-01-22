package com.jumunhasyeo.hub.hubRoute.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.testsupport.AbstractEventDispatchIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

public class HubRouteKafkaEventListenerIntegrationTest extends AbstractEventDispatchIntegrationTest {

    @Autowired
    private HubRouteKafkaEventListener hubRouteKafkaEventListener;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetHandlers() {
        reset(hubRouteEventHandler);
    }

    @Test
    @DisplayName("HubCreatedEvent를 수신하고 처리할 수 있다.")
    void dispatch_HubCreatedEvent_integration_success() throws Exception {
        //given
        Hub hub = createHub();
        HubCreatedEvent event = HubCreatedEvent.centerHub(hub);
        String payload = objectMapper.writeValueAsString(event);
        String simpleClassName = "HubCreatedEvent";

        //when
        hubRouteKafkaEventListener.dispatch(payload, simpleClassName);

        //then
        then(hubRouteEventHandler).should(times(1)).hubCreated(any(HubCreatedEvent.class));
    }

    @Test
    @DisplayName("HubDeletedEvent를 수신하고 처리할 수 있다.")
    void dispatch_HubDeletedEvent_integration_success() throws Exception {
        //given
        Hub hub = createHub();
        HubDeletedEvent event = HubDeletedEvent.from(hub, 1L);
        String payload = objectMapper.writeValueAsString(event);
        String simpleClassName = "HubDeletedEvent";

        //when
        hubRouteKafkaEventListener.dispatch(payload, simpleClassName);

        //then
        then(hubRouteEventHandler).should(times(1)).hubDeleted(any(HubDeletedEvent.class));
    }

    @Test
    @DisplayName("eventType이 null이면 이벤트를 건너뛴다.")
    void listen_WhenEventTypeNull_skip() throws Exception {
        hubRouteKafkaEventListener.listen("{}", null);

        then(hubRouteEventHandler).should(never()).hubCreated(any());
        then(hubRouteEventHandler).should(never()).hubDeleted(any());
    }

    private static Hub createHub() {
        return Hub.builder()
                .hubId(UUID.randomUUID())
                .name("테스트 허브")
                .hubType(HubType.CENTER)
                .address(Address.of("서울시", Coordinate.of(37.5, 127.0)))
                .build();
    }
}
