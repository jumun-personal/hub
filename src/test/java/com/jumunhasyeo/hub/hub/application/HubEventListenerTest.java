package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxPublicationService;
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
class HubEventListenerTest {

    @Mock
    private OutboxPublicationService outboxPublicationService;

    @InjectMocks
    private HubEventListener hubEventListener;

    @Test
    @DisplayName("HubCreatedEvent는 BEFORE_COMMIT에서 Outbox에 저장된다.")
    void handleBeforeCommit_hubCreated_save() {
        HubCreatedEvent event = HubCreatedEvent.centerHub(createHub());

        hubEventListener.handleBeforeCommit(event);

        then(outboxPublicationService).should().append(event);
    }

    @Test
    @DisplayName("HubDeletedEvent는 BEFORE_COMMIT에서 Outbox에 저장된다.")
    void handleBeforeCommit_hubDeleted_save() {
        HubDeletedEvent event = HubDeletedEvent.from(createHub(), 1L);

        hubEventListener.handleBeforeCommit(event);

        then(outboxPublicationService).should().append(event);
    }

    @Test
    @DisplayName("HubNameUpdatedEvent는 BEFORE_COMMIT에서 Outbox에 저장된다.")
    void handleBeforeCommit_hubNameUpdated_save() {
        HubNameUpdatedEvent event = HubNameUpdatedEvent.of(createHub());

        hubEventListener.handleBeforeCommit(event);

        then(outboxPublicationService).should().append(event);
    }

    @Test
    @DisplayName("HubUpdatedEvent는 BEFORE_COMMIT에서 Outbox에 저장된다.")
    void handleBeforeCommit_hubUpdated_save() {
        HubUpdatedEvent event = HubUpdatedEvent.of(createHub());

        hubEventListener.handleBeforeCommit(event);

        then(outboxPublicationService).should().append(event);
    }

    @Test
    @DisplayName("HubCreatedEvent는 AFTER_COMMIT에서 Outbox 발행 처리된다.")
    void handleAfterCommit_hubCreated_publish() {
        HubCreatedEvent event = HubCreatedEvent.centerHub(createHub());

        hubEventListener.handleAfterCommit(event);

        then(outboxPublicationService).should().publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubDeletedEvent는 AFTER_COMMIT에서 Outbox 발행 처리된다.")
    void handleAfterCommit_hubDeleted_publish() {
        HubDeletedEvent event = HubDeletedEvent.from(createHub(), 1L);

        hubEventListener.handleAfterCommit(event);

        then(outboxPublicationService).should().publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubNameUpdatedEvent는 AFTER_COMMIT에서 Outbox 발행 처리된다.")
    void handleAfterCommit_hubNameUpdated_publish() {
        HubNameUpdatedEvent event = HubNameUpdatedEvent.of(createHub());

        hubEventListener.handleAfterCommit(event);

        then(outboxPublicationService).should().publishAfterCommit(event.getEventKey());
    }

    @Test
    @DisplayName("HubUpdatedEvent는 AFTER_COMMIT에서 Outbox 발행 처리된다.")
    void handleAfterCommit_hubUpdated_publish() {
        HubUpdatedEvent event = HubUpdatedEvent.of(createHub());

        hubEventListener.handleAfterCommit(event);

        then(outboxPublicationService).should().publishAfterCommit(event.getEventKey());
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
