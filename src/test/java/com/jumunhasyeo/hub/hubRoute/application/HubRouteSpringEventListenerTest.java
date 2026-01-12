package com.jumunhasyeo.hub.hubRoute.application;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxService;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteCreatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDeletedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class HubRouteSpringEventListenerTest {

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private HubRouteSpringEventListener hubRouteSpringEventListener;

    @Test
    @DisplayName("HubRouteCreatedEvent를 Outbox에 저장할 수 있다.")
    void handleHubRouteCreated_success() {
        //given
        HubRouteCreatedEvent event = createHubRouteCreatedEvent();

        //when
        hubRouteSpringEventListener.handleHubRouteCreated(event);

        //then
        then(outboxService).should(times(1)).save(any(HubRouteCreatedEvent.class));
    }

    @Test
    @DisplayName("HubRouteDeletedEvent 리스트를 Outbox에 저장할 수 있다.")
    void handleHubRouteDeleted_success() {
        //given
        HubRouteDeletedEvent event = createHubRouteDeletedEvent();

        //when
        hubRouteSpringEventListener.handleHubRouteDeleted(event);

        //then
        then(outboxService).should(times(1)).save(any(HubRouteDeletedEvent.class));
    }

    @Test
    @DisplayName("HubRouteCreatedEvent는 커밋 후 Outbox 발행 처리가 호출된다.")
    void asyncHandleHubRouteCreated_success() {
        //given
        HubRouteCreatedEvent event = createHubRouteCreatedEvent();

        //when
        hubRouteSpringEventListener.asyncHandleHubRouteCreated(event);

        //then
        then(outboxService).should(times(1)).publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubRouteCreatedEvent는 커밋 후 Outbox 발행 처리가 호출된다.")
    void asyncHandleHubRouteCreated_WhenKafkaFails_doesNotMarkAsProcessed() {
        //given
        HubRouteCreatedEvent event = createHubRouteCreatedEvent();

        //when
        hubRouteSpringEventListener.asyncHandleHubRouteCreated(event);

        //then
        then(outboxService).should(times(1)).publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubRouteDeletedEvent는 커밋 후 Outbox 발행 처리가 호출된다.")
    void asyncHandleHubRouteDeleted_success() {
        //given
        HubRouteDeletedEvent event = createHubRouteDeletedEvent();

        //when
        hubRouteSpringEventListener.asyncHandleHubRouteDeleted(event);

        //then
        then(outboxService).should(times(1)).publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubRouteDeletedEvent는 커밋 후 Outbox 발행 처리가 호출된다.")
    void asyncHandleHubRouteDeleted_WhenKafkaFails_doesNotMarkAsProcessed() {
        //given
        HubRouteDeletedEvent event = createHubRouteDeletedEvent();

        //when
        hubRouteSpringEventListener.asyncHandleHubRouteDeleted(event);

        //then
        then(outboxService).should(times(1)).publishAfterCommit(event.getEventKey());
    }

    private static HubRouteCreatedEvent createHubRouteCreatedEvent() {
        HubRoute route = createHubRoute();
        return HubRouteCreatedEvent.from(route);
    }

    private static HubRouteDeletedEvent createHubRouteDeletedEvent() {
        HubRoute route = createHubRoute();
        return HubRouteDeletedEvent.from(route);
    }

    private static HubRoute createHubRoute() {
        Hub startHub = createHub();
        Hub endHub = createHub();
        RouteWeight weight = RouteWeight.of(BigDecimal.valueOf(30) ,5);
        
        return HubRoute.builder()
                .routeId(UUID.randomUUID())
                .startHub(startHub)
                .endHub(endHub)
                .routeWeight(weight)
                .build();
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
