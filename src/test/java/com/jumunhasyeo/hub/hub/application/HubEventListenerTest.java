package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.common.outbox.OutboxService;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubNameUpdatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubUpdatedEvent;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hub.infrastructure.event.KafkaHubEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class HubEventListenerTest {

    @Mock
    private KafkaHubEventPublisher kafkaHubEventPublisher;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private HubEventListener hubEventListener;

    @Test
    @DisplayName("HubCreatedEvent를 Outbox에 저장할 수 있다.")
    void handleHubCreated_success() {
        //given
        Hub hub = createHub();
        HubCreatedEvent event = HubCreatedEvent.centerHub(hub);

        //when
        hubEventListener.handleHubCreated(event);

        //then
        then(outboxService).should().save(event);
    }

    @Test
    @DisplayName("HubDeletedEvent를 Outbox에 저장할 수 있다.")
    void handleHubDeleted_success() {
        //given
        Hub hub = createHub();
        HubDeletedEvent event = HubDeletedEvent.from(hub, 1L);

        //when
        hubEventListener.handleHubDeleted(event);

        //then
        then(outboxService).should().save(event);
    }

    @Test
    @DisplayName("HubNameUpdatedEvent를 Outbox에 저장할 수 있다.")
    void handleHubNameUpdated_success() {
        //given
        Hub hub = createHub();
        HubNameUpdatedEvent event = HubNameUpdatedEvent.of(hub);

        //when
        hubEventListener.handleHubNameUpdated(event);

        //then
        then(outboxService).should().save(event);
    }

    @Test
    @DisplayName("HubUpdatedEvent를 Outbox에 저장할 수 있다.")
    void handleHubUpdated_success() {
        Hub hub = createHub();
        HubUpdatedEvent event = HubUpdatedEvent.of(hub);

        hubEventListener.handleHubUpdated(event);

        then(outboxService).should().save(event);
    }

    @Test
    @DisplayName("HubCreatedEvent를 Kafka로 발행하고 성공 시 Outbox를 완료 처리한다.")
    void asyncHandleHubCreated_success() {
        //given
        Hub hub = createHub();
        HubCreatedEvent event = HubCreatedEvent.centerHub(hub);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        given(kafkaHubEventPublisher.publishEvent(event)).willReturn(future);

        //when
        hubEventListener.asyncHandleHubCreated(event);

        //then
        then(kafkaHubEventPublisher).should().publishEvent(event);
        then(outboxService).should().markAsProcessed(event.getEventKey());
    }

    @Test
    @DisplayName("HubCreatedEvent Kafka 발행 실패 시 Outbox를 완료 처리하지 않는다.")
    void asyncHandleHubCreated_WhenKafkaFails_doesNotMarkAsProcessed() {
        //given
        Hub hub = createHub();
        HubCreatedEvent event = HubCreatedEvent.centerHub(hub);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.failedFuture(
                new RuntimeException("Kafka error")
        );
        given(kafkaHubEventPublisher.publishEvent(event)).willReturn(future);

        //when
        hubEventListener.asyncHandleHubCreated(event);

        //then
        then(kafkaHubEventPublisher).should().publishEvent(event);
        then(outboxService).should(never()).markAsProcessed(any());
    }

    @Test
    @DisplayName("HubDeletedEvent를 Kafka로 발행하고 성공 시 Outbox를 완료 처리한다.")
    void asyncHandleHubDeleted_success() {
        //given
        Hub hub = createHub();
        HubDeletedEvent event = HubDeletedEvent.from(hub, 1L);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        given(kafkaHubEventPublisher.publishEvent(event)).willReturn(future);

        //when
        hubEventListener.asyncHandleHubDeleted(event);

        //then
        then(kafkaHubEventPublisher).should().publishEvent(event);
        then(outboxService).should().markAsProcessed(event.getEventKey());
    }

    @Test
    @DisplayName("HubNameUpdatedEvent를 Kafka로 발행하고 성공 시 Outbox를 완료 처리한다.")
    void asyncHandleNameUpdated_success() {
        //given
        Hub hub = createHub();
        HubNameUpdatedEvent event = HubNameUpdatedEvent.of(hub);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        given(kafkaHubEventPublisher.publishEvent(event)).willReturn(future);

        //when
        hubEventListener.asyncHandleNameUpdated(event);

        //then
        then(kafkaHubEventPublisher).should().publishEvent(event);
        then(outboxService).should().markAsProcessed(event.getEventKey());
    }

    @Test
    @DisplayName("HubUpdatedEvent를 Kafka로 발행하고 성공 시 Outbox를 완료 처리한다.")
    void asyncHandleHubUpdated_success() {
        Hub hub = createHub();
        HubUpdatedEvent event = HubUpdatedEvent.of(hub);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        given(kafkaHubEventPublisher.publishEvent(event)).willReturn(future);

        hubEventListener.asyncHandleHubUpdated(event);

        then(kafkaHubEventPublisher).should().publishEvent(event);
        then(outboxService).should().markAsProcessed(event.getEventKey());
    }

    @Test
    @DisplayName("HubUpdatedEvent Kafka 발행 실패 시 Outbox를 완료 처리하지 않는다.")
    void asyncHandleHubUpdated_fail() {
        Hub hub = createHub();
        HubUpdatedEvent event = HubUpdatedEvent.of(hub);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.failedFuture(
                new RuntimeException("Kafka error")
        );
        given(kafkaHubEventPublisher.publishEvent(event)).willReturn(future);

        hubEventListener.asyncHandleHubUpdated(event);

        then(kafkaHubEventPublisher).should().publishEvent(event);
        then(outboxService).should(never()).markAsProcessed(any());
    }

    @Test
    @DisplayName("HubDeletedEvent Kafka 발행 실패 시 Outbox를 완료 처리하지 않는다.")
    void asyncHandleHubDeleted_fail() {
        Hub hub = createHub();
        HubDeletedEvent event = HubDeletedEvent.from(hub, 1L);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.failedFuture(
                new RuntimeException("Kafka error")
        );
        given(kafkaHubEventPublisher.publishEvent(event)).willReturn(future);

        hubEventListener.asyncHandleHubDeleted(event);

        then(kafkaHubEventPublisher).should().publishEvent(event);
        then(outboxService).should(never()).markAsProcessed(any());
    }

    @Test
    @DisplayName("HubNameUpdatedEvent Kafka 발행 실패 시 Outbox를 완료 처리하지 않는다.")
    void asyncHandleNameUpdated_fail() {
        Hub hub = createHub();
        HubNameUpdatedEvent event = HubNameUpdatedEvent.of(hub);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.failedFuture(
                new RuntimeException("Kafka error")
        );
        given(kafkaHubEventPublisher.publishEvent(event)).willReturn(future);

        hubEventListener.asyncHandleNameUpdated(event);

        then(kafkaHubEventPublisher).should().publishEvent(event);
        then(outboxService).should(never()).markAsProcessed(any());
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
