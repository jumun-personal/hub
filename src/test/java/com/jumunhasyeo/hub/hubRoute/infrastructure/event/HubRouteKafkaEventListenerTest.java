package com.jumunhasyeo.hub.hubRoute.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class HubRouteKafkaEventListenerTest {

    @Mock
    private HubRouteEventHandler hubRouteEventHandler;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private HubRouteKafkaEventListener hubRouteKafkaEventListener;

    @Test
    @DisplayName("HubCreatedEvent를 처리할 수 있다.")
    void dispatch_HubCreatedEvent_success() throws Exception {
        //given
        String payload = "{\"hubId\":\"123\"}";
        String simpleClassName = "HubCreatedEvent";
        Hub hub = createHub();
        HubCreatedEvent event = HubCreatedEvent.centerHub(hub);
        given(objectMapper.readValue(payload, HubCreatedEvent.class)).willReturn(event);

        //when
        hubRouteKafkaEventListener.dispatch(payload, simpleClassName);

        //then
        then(hubRouteEventHandler).should().hubCreated(event);
    }

    @Test
    @DisplayName("HubDeletedEvent를 처리할 수 있다.")
    void dispatch_HubDeletedEvent_success() throws Exception {
        //given
        String payload = "{\"hubId\":\"123\"}";
        String simpleClassName = "HubDeletedEvent";
        Hub hub = createHub();
        HubDeletedEvent event = HubDeletedEvent.from(hub, 1L);
        given(objectMapper.readValue(payload, HubDeletedEvent.class)).willReturn(event);

        //when
        hubRouteKafkaEventListener.dispatch(payload, simpleClassName);

        //then
        then(hubRouteEventHandler).should().hubDeleted(event);
    }

    @Test
    @DisplayName("처리할 수 없는 이벤트 타입은 무시된다.")
    void dispatch_UnknownEventType_ignored() throws Exception {
        //given
        String payload = "{\"data\":\"test\"}";
        String simpleClassName = "UnknownEvent";

        //when
        hubRouteKafkaEventListener.dispatch(payload, simpleClassName);

        //then
        then(hubRouteEventHandler).should(never()).hubCreated(any());
        then(hubRouteEventHandler).should(never()).hubDeleted(any());
    }

    @Test
    @DisplayName("eventType 헤더가 없으면 listen은 skip 한다.")
    void listen_WhenHeaderMissing_skip() throws Exception {
        hubRouteKafkaEventListener.listen("{}", null);

        then(hubRouteEventHandler).should(never()).hubCreated(any());
        then(hubRouteEventHandler).should(never()).hubDeleted(any());
    }

    @Test
    @DisplayName("미지원 eventType이면 listen은 skip 한다.")
    void listen_WhenUnsupportedType_skip() throws Exception {
        hubRouteKafkaEventListener.listen("{}", "UNKNOWN");

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
