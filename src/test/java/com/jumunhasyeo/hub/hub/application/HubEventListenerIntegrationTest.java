package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.hub.infrastructure.outbox.JpaOutboxRepository;
import com.jumunhasyeo.hub.infrastructure.outbox.OutboxEvent;
import com.jumunhasyeo.hub.infrastructure.outbox.OutboxStatus;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubNameUpdatedEvent;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static com.jumunhasyeo.hub.hubRoute.infrastructure.event.ListenEventRegistry.HUB_CREATED_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

public class HubEventListenerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private HubEventListener hubEventListener;

    @Autowired
    private JpaOutboxRepository jpaOutboxRepository;

    @Test
    @DisplayName("HubCreatedEvent를 수신하면 Outbox에 저장된다.")
    void handleHubCreated_integration_success() {
        //given
        Hub hub = createHub();
        HubCreatedEvent event = HubCreatedEvent.centerHub(hub);

        //when
        hubEventListener.handleHubCreated(event);

        //then
        List<OutboxEvent> outboxEvents = jpaOutboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventName()).isEqualTo(HUB_CREATED_EVENT.getEventName());
        assertThat(outboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("HubDeletedEvent를 수신하면 Outbox에 저장된다.")
    void handleHubDeleted_integration_success() {
        //given
        Hub hub = createHub();
        HubDeletedEvent event = HubDeletedEvent.from(hub, 1L);

        //when
        hubEventListener.handleHubDeleted(event);

        //then
        List<OutboxEvent> outboxEvents = jpaOutboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventName()).isEqualTo("HubDeletedEvent");
        assertThat(outboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("HubNameUpdatedEvent를 수신하면 Outbox에 저장된다.")
    void handleHubNameUpdated_integration_success() {
        //given
        Hub hub = createHub();
        HubNameUpdatedEvent event = HubNameUpdatedEvent.of(hub);

        //when
        hubEventListener.handleHubNameUpdated(event);

        //then
        List<OutboxEvent> outboxEvents = jpaOutboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventName()).isEqualTo("HubNameUpdatedEvent");
        assertThat(outboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
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
