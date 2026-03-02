package com.jumunhasyeo.hub.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubStatus;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hub.infrastructure.repository.JpaHubRepository;
import com.jumunhasyeo.testsupport.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jumunhasyeo.hub.hubRoute.infrastructure.event.ListenEventRegistry.HUB_CREATED_EVENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

public class OutboxPublicationServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private OutboxPublicationService outboxService;

    @Autowired
    private JpaOutboxRepository jpaOutboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JpaHubRepository jpaHubRepository;

    @Test
    @DisplayName("HubCreatedEvent를 저장하고 조회할 수 있다.")
    void save_HubCreatedEvent_integration_success() {
        //given
        Hub hub = createHub();
        HubCreatedEvent event = HubCreatedEvent.centerHub(hub);

        //when
        outboxService.append(event);

        //then
        List<OutboxEvent> events = jpaOutboxRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventName()).isEqualTo(HUB_CREATED_EVENT.getEventName());
        assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("HubDeletedEvent를 저장하고 조회할 수 있다.")
    void save_HubDeletedEvent_integration_success() {
        //given
        Hub hub = createHub();
        HubDeletedEvent event = HubDeletedEvent.from(hub, 1L);

        //when
        outboxService.append(event);

        //then
        List<OutboxEvent> events = jpaOutboxRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventName()).isEqualTo("HubDeletedEvent");
        assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("완료된 이벤트를 정리할 수 있다.")
    void cleanUp_integration_success() {
        //given
        OutboxEvent completedEvent = OutboxEvent.of(
                "HubCreatedEvent",
                "{\"hubId\":\"123\"}",
                "completed-key",
                "hub"
        );
        completedEvent.markProcessed();
        jpaOutboxRepository.save(completedEvent);

        OutboxEvent pendingEvent = OutboxEvent.of(
                "HubCreatedEvent",
                "{\"hubId\":\"456\"}",
                "pending-key",
                "hub"
        );
        jpaOutboxRepository.save(pendingEvent);

        LocalDateTime cutoff = LocalDateTime.now().plusMinutes(1);

        //when
        int deletedCount = outboxService.cleanupCompletedBefore(cutoff);

        //then
        assertThat(deletedCount).isEqualTo(1);
        List<OutboxEvent> remainingEvents = jpaOutboxRepository.findAll();
        assertThat(remainingEvents).hasSize(1);
        assertThat(remainingEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("재시도 가능한 이벤트를 처리하면 COMPLETE 상태가 된다.")
    void publishClaimedEvent_integration_success() {
        //given
        doNothing().when(outboxDispatcher).dispatch(any());

        OutboxEvent event = OutboxEvent.of(
                "test-topic",
                "{\"data\":\"test\"}",
                "test-key",
                "hub"
        );
        jpaOutboxRepository.save(event);

        //when
        outboxService.publishClaimedEvent(event);

        //then
        OutboxEvent savedEvent = jpaOutboxRepository.findByEventKey(event.getEventKey()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.COMPLETE);
        assertThat(savedEvent.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("재시도 불가능한 이벤트는 DEAD 상태가 된다.")
    void publishClaimedEvent_WhenCannotRetry_integration_marksDead() {
        //given
        doThrow(new RuntimeException("Dispatch failed"))
                .when(outboxDispatcher).dispatch(any());
        OutboxEvent event = OutboxEvent.of(
                "test-topic",
                "{\"data\":\"test\"}",
                "test-key-2",
                "hub"
        );
        event.incrementRetryCount();
        event.incrementRetryCount();
        event.incrementRetryCount();
        jpaOutboxRepository.save(event);

        //when
        outboxService.publishClaimedEvent(event);

        //then
        OutboxEvent savedEvent = jpaOutboxRepository.findByEventKey(event.getEventKey()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(savedEvent.getErrorMessage()).isEqualTo("Max retry count exceeded");
    }

    @Test
    @DisplayName("Hub 생성 Outbox가 DEAD가 되면 PENDING Hub를 FAILED로 전환한다.")
    void publishClaimedEvent_WhenHubCreatedEventBecomesDead_marksHubFailed() {
        // given
        Hub hub = jpaHubRepository.save(Hub.of(
                "Outbox 실패 허브",
                Address.of("서울시", Coordinate.of(37.5, 127.0)),
                HubType.CENTER
        ));
        HubCreatedEvent createdEvent = HubCreatedEvent.centerHub(hub);
        outboxService.append(createdEvent);

        OutboxEvent outboxEvent = jpaOutboxRepository.findByEventKey(createdEvent.getEventKey()).orElseThrow();
        outboxEvent.incrementRetryCount();
        outboxEvent.incrementRetryCount();
        outboxEvent.incrementRetryCount();
        jpaOutboxRepository.saveAndFlush(outboxEvent);

        // when
        outboxService.publishClaimedEvent(outboxEvent);

        // then
        Hub failedHub = jpaHubRepository.findByIdIncludingDeleted(hub.getHubId()).orElseThrow();
        assertThat(failedHub.getStatus()).isEqualTo(HubStatus.FAILED);
        assertThat(failedHub.isDeleted()).isTrue();
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
