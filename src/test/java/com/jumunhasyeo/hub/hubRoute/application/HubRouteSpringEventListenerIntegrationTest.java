package com.jumunhasyeo.hub.hubRoute.application;

import com.jumunhasyeo.hub.infrastructure.outbox.JpaOutboxRepository;
import com.jumunhasyeo.hub.infrastructure.outbox.OutboxEvent;
import com.jumunhasyeo.hub.infrastructure.outbox.OutboxStatus;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteCreatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDeletedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import com.jumunhasyeo.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class HubRouteSpringEventListenerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private HubRouteSpringEventListener hubRouteSpringEventListener;

    @Autowired
    private JpaOutboxRepository jpaOutboxRepository;

    @Test
    @DisplayName("HubRouteCreatedEvent를 수신하면 Outbox에 저장된다.")
    void handleHubRouteCreated_integration_success() {
        //given
        HubRoute route1 = createHubRoute();
        HubRouteCreatedEvent event = HubRouteCreatedEvent.from(route1);

        //when
        hubRouteSpringEventListener.handleBeforeCommit(event);

        //then
        List<OutboxEvent> outboxEvents = jpaOutboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventName()).isEqualTo("HubRouteCreatedEvent");
        assertThat(outboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("HubRouteDeletedEvent를 수신하면 Outbox에 저장된다.")
    void handleHubRouteDeleted_integration_success() {
        //given
        HubRoute route1 = createHubRoute();
        HubRouteDeletedEvent event = HubRouteDeletedEvent.from(route1);

        //when
        hubRouteSpringEventListener.handleBeforeCommit(event);

        //then
        List<OutboxEvent> outboxEvents = jpaOutboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventName()).isEqualTo("HubRouteDeletedEvent");
        assertThat(outboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
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
