package com.jumunhasyeo.hub.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.jumunhasyeo.hub.hubRoute.infrastructure.event.ListenEventRegistry.HUB_CREATED_EVENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxPublicationServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private JpaOutboxRepository outboxRepository;

    @Mock
    private OutboxStateTransitions outboxStateTransitions;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OutboxDispatcher outboxDispatcher;

    @InjectMocks
    private OutboxPublicationService outboxService;

    @Test
    @DisplayName("HubCreatedEvent를 저장할 수 있다.")
    void save_HubCreatedEvent_success() throws JsonProcessingException {
        //given
        Hub hub = createHub();
        HubCreatedEvent event = HubCreatedEvent.centerHub(hub);
        String expectedJson = "{\"hubId\":\"" + hub.getHubId() + "\"}";
        given(objectMapper.writeValueAsString(event)).willReturn(expectedJson);

        //when
        outboxService.append(event);

        //then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        then(outboxRepository).should().save(captor.capture());

        OutboxEvent savedEvent = captor.getValue();
        assertThat(savedEvent.getEventName()).isEqualTo(HUB_CREATED_EVENT.getEventName());
        assertThat(savedEvent.getPayload()).isEqualTo(expectedJson);
        assertThat(savedEvent.getEventKey()).isEqualTo(event.getEventKey());
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("HubDeletedEvent를 저장할 수 있다.")
    void save_HubDeletedEvent_success() throws JsonProcessingException {
        //given
        Hub hub = createHub();
        HubDeletedEvent event = HubDeletedEvent.from(hub, 1L);
        String expectedJson = "{\"hubId\":\"" + hub.getHubId() + "\"}";
        given(objectMapper.writeValueAsString(event)).willReturn(expectedJson);

        //when
        outboxService.append(event);

        //then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        then(outboxRepository).should().save(captor.capture());

        OutboxEvent savedEvent = captor.getValue();
        assertThat(savedEvent.getEventName()).isEqualTo("HubDeletedEvent");
        assertThat(savedEvent.getPayload()).isEqualTo(expectedJson);
    }

    @Test
    @DisplayName("JSON 직렬화 실패 시 예외가 발생한다.")
    void save_JsonProcessingException_throwsBusinessException() throws JsonProcessingException {
        //given
        Hub hub = createHub();
        HubCreatedEvent event = HubCreatedEvent.centerHub(hub);
        given(objectMapper.writeValueAsString(event))
                .willThrow(new JsonProcessingException("Serialization error") {});

        //when & then
        assertThatThrownBy(() -> outboxService.append(event))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to serialize event payload");
    }

    @Test
    @DisplayName("클레임된 이벤트를 발행할 수 있다.")
    void publishClaimedEvent_success() throws Exception {
        //given
        OutboxEvent event = createOutboxEvent();
        doNothing().when(outboxDispatcher).dispatch(event);
        doAnswer(invocation -> {
            event.publishSuccess();
            return null;
        }).when(outboxStateTransitions).markPublishSuccess(event);

        //when
        OutboxEvent outboxEvent = outboxService.publishClaimedEvent(event);

        //then
        then(outboxStateTransitions).should().markPublishSuccess(event);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.COMPLETE);
    }

    @Test
    @DisplayName("재시도 불가능한 이벤트는 최종 실패 처리된다.")
    void publishClaimedEvent_WhenCannotRetry_marksDead() {
        //given
        OutboxEvent event = createOutboxEvent();
        event.incrementRetryCount();
        event.incrementRetryCount();
        event.incrementRetryCount();
        doAnswer(invocation -> {
            event.markDead("Max retry count exceeded");
            return null;
        }).when(outboxStateTransitions).markPublishDead(event, "Max retry count exceeded");

        //when
        outboxService.publishClaimedEvent(event);

        //then
        then(kafkaTemplate).should(never()).send(anyString(), anyString());
        then(outboxStateTransitions).should().markPublishDead(event, "Max retry count exceeded");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(event.getErrorMessage()).isEqualTo("Max retry count exceeded");
    }

    @Test
    @DisplayName("Kafka 발행 실패 시 실패 상태 저장을 위임한다.")
    void publishClaimedEvent_WhenKafkaFails_publishFail() {
        //given
        OutboxEvent event = createOutboxEvent();
        doThrow(new RuntimeException("에러")).when(outboxDispatcher).dispatch(event);
        doAnswer(invocation -> {
            event.publishFail("에러");
            return null;
        }).when(outboxStateTransitions).markPublishFailure(event, "에러");

        //when
        OutboxEvent outboxEvent = outboxService.publishClaimedEvent(event);

        //then
        then(outboxStateTransitions).should().markPublishFailure(event, "에러");
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getErrorMessage()).isEqualTo("에러");
    }

    @Test
    @DisplayName("After Commit 발행은 클레임 성공 시에만 Kafka 발행을 수행한다.")
    void publishAfterCommit_WhenClaimSuccess_publishesEvent() {
        // given
        String eventKey = "test-key";
        OutboxEvent event = createOutboxEvent();
        given(outboxStateTransitions.claimByEventKey(eq(eventKey), any(LocalDateTime.class)))
                .willReturn(Optional.of(event));

        // when
        outboxService.publishAfterCommit(eventKey);

        // then
        then(outboxDispatcher).should().dispatch(event);
    }

    @Test
    @DisplayName("After Commit 발행은 클레임 실패 시 Kafka 발행을 건너뛴다.")
    void publishAfterCommit_WhenClaimFails_skipsPublish() {
        // given
        String eventKey = "test-key";
        given(outboxStateTransitions.claimByEventKey(eq(eventKey), any(LocalDateTime.class)))
                .willReturn(Optional.empty());

        // when
        outboxService.publishAfterCommit(eventKey);

        // then
        then(outboxDispatcher).should(never()).dispatch(any());
    }

    @Test
    @DisplayName("완료된 이벤트를 정리할 수 있다.")
    void cleanUp_success() {
        //given
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        given(outboxRepository.deleteByStatusAndCreatedAtBefore(OutboxStatus.COMPLETE, cutoff))
                .willReturn(10);

        //when
        int deletedCount = outboxService.cleanupCompletedBefore(cutoff);

        //then
        assertThat(deletedCount).isEqualTo(10);
    }

    @Test
    @DisplayName("스케줄러 발행은 클레임된 이벤트만 처리한다.")
    void processClaimableEvents_publishesClaimedEvents() {
        // given
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(5);
        OutboxEvent event = createOutboxEvent();
        given(outboxStateTransitions.claimPublishableEvents(staleBefore)).willReturn(List.of(event));

        // when
        outboxService.publishPending(staleBefore);

        // then
        then(outboxDispatcher).should().dispatch(event);
    }

    private static OutboxEvent createOutboxEvent() {
        return OutboxEvent.of(
                "HubCreatedEvent",
                "{\"hubId\":\"123\"}",
                "test-key",
                "hub"
        );
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
