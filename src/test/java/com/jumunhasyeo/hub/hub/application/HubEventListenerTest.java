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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
public class HubEventListenerTest {

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
    @DisplayName("HubCreatedEvent를 커밋 후 Outbox 발행 처리한다.")
    void asyncHandleHubCreated_success() {
        //given
        Hub hub = createHub();
        HubCreatedEvent event = HubCreatedEvent.centerHub(hub);

        //when
        hubEventListener.asyncHandleHubCreated(event);

        //then
        then(outboxService).should().publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubCreatedEvent는 커밋 후 Outbox 발행 처리가 호출된다.")
    void asyncHandleHubCreated_WhenKafkaFails_doesNotMarkAsProcessed() {
        //given
        Hub hub = createHub();
        HubCreatedEvent event = HubCreatedEvent.centerHub(hub);

        //when
        hubEventListener.asyncHandleHubCreated(event);

        //then
        then(outboxService).should().publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubDeletedEvent는 커밋 후 Outbox 발행 처리가 호출된다.")
    void asyncHandleHubDeleted_success() {
        //given
        Hub hub = createHub();
        HubDeletedEvent event = HubDeletedEvent.from(hub, 1L);

        //when
        hubEventListener.asyncHandleHubDeleted(event);

        //then
        then(outboxService).should().publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubNameUpdatedEvent는 커밋 후 Outbox 발행 처리가 호출된다.")
    void asyncHandleNameUpdated_success() {
        //given
        Hub hub = createHub();
        HubNameUpdatedEvent event = HubNameUpdatedEvent.of(hub);

        //when
        hubEventListener.asyncHandleNameUpdated(event);

        //then
        then(outboxService).should().publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubUpdatedEvent는 커밋 후 Outbox 발행 처리가 호출된다.")
    void asyncHandleHubUpdated_success() {
        Hub hub = createHub();
        HubUpdatedEvent event = HubUpdatedEvent.of(hub);

        hubEventListener.asyncHandleHubUpdated(event);

        then(outboxService).should().publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubUpdatedEvent는 커밋 후 Outbox 발행 처리가 호출된다.")
    void asyncHandleHubUpdated_fail() {
        Hub hub = createHub();
        HubUpdatedEvent event = HubUpdatedEvent.of(hub);

        hubEventListener.asyncHandleHubUpdated(event);

        then(outboxService).should().publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubDeletedEvent는 커밋 후 Outbox 발행 처리가 호출된다.")
    void asyncHandleHubDeleted_fail() {
        Hub hub = createHub();
        HubDeletedEvent event = HubDeletedEvent.from(hub, 1L);

        hubEventListener.asyncHandleHubDeleted(event);

        then(outboxService).should().publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubNameUpdatedEvent는 커밋 후 Outbox 발행 처리가 호출된다.")
    void asyncHandleNameUpdated_fail() {
        Hub hub = createHub();
        HubNameUpdatedEvent event = HubNameUpdatedEvent.of(hub);

        hubEventListener.asyncHandleNameUpdated(event);

        then(outboxService).should().publishAfterCommit(event.getEventKey());
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
