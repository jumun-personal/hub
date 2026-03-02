package com.jumunhasyeo.hub.hubRoute.application;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxPublicationService;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildCompletedEvent;
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

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class HubRouteSpringEventListenerTest {

    @Mock
    private OutboxPublicationService outboxPublicationService;

    @InjectMocks
    private HubRouteSpringEventListener hubRouteSpringEventListener;

    @Test
    @DisplayName("HubRouteCreatedEvent는 BEFORE_COMMIT에서 Outbox에 저장된다.")
    void handleBeforeCommit_created_save() {
        HubRouteCreatedEvent event = createHubRouteCreatedEvent();

        hubRouteSpringEventListener.handleBeforeCommit(event);

        then(outboxPublicationService).should().append(event);
    }

    @Test
    @DisplayName("HubRouteDeletedEvent는 BEFORE_COMMIT에서 Outbox에 저장된다.")
    void handleBeforeCommit_deleted_save() {
        HubRouteDeletedEvent event = createHubRouteDeletedEvent();

        hubRouteSpringEventListener.handleBeforeCommit(event);

        then(outboxPublicationService).should().append(event);
    }

    @Test
    @DisplayName("HubRouteBuildCompletedEvent는 BEFORE_COMMIT에서 Outbox에 저장된다.")
    void handleBeforeCommit_buildCompleted_save() {
        HubRouteBuildCompletedEvent event = new HubRouteBuildCompletedEvent(UUID.randomUUID(), UUID.randomUUID(), HubType.CENTER);

        hubRouteSpringEventListener.handleBeforeCommit(event);

        then(outboxPublicationService).should().append(event);
    }

    @Test
    @DisplayName("HubRouteCreatedEvent는 AFTER_COMMIT에서 Outbox 발행 처리된다.")
    void handleAfterCommit_created_publish() {
        HubRouteCreatedEvent event = createHubRouteCreatedEvent();

        hubRouteSpringEventListener.handleAfterCommit(event);

        then(outboxPublicationService).should().publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubRouteDeletedEvent는 AFTER_COMMIT에서 Outbox 발행 처리된다.")
    void handleAfterCommit_deleted_publish() {
        HubRouteDeletedEvent event = createHubRouteDeletedEvent();

        hubRouteSpringEventListener.handleAfterCommit(event);

        then(outboxPublicationService).should().publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubRouteBuildCompletedEvent는 AFTER_COMMIT에서 Outbox 발행 처리된다.")
    void handleAfterCommit_buildCompleted_publish() {
        HubRouteBuildCompletedEvent event = new HubRouteBuildCompletedEvent(UUID.randomUUID(), UUID.randomUUID(), HubType.CENTER);

        hubRouteSpringEventListener.handleAfterCommit(event);

        then(outboxPublicationService).should().publishAfterCommit(event.getEventKey());
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
        RouteWeight weight = RouteWeight.of(BigDecimal.valueOf(30), 5);

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
