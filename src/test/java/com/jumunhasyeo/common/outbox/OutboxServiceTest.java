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
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.jumunhasyeo.hub.hubRoute.infrastructure.event.ListenEventRegistry.HUB_CREATED_EVENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OutboxDispatcher outboxDispatcher;

    @InjectMocks
    private OutboxService outboxService;

    @Test
    @DisplayName("HubCreatedEvent를 저장할 수 있다.")
    void save_HubCreatedEvent_success() throws JsonProcessingException {
        //given
        Hub hub = createHub();
        HubCreatedEvent event = HubCreatedEvent.centerHub(hub);
        String expectedJson = "{\"hubId\":\"" + hub.getHubId() + "\"}";
        given(objectMapper.writeValueAsString(event)).willReturn(expectedJson);

        //when
        outboxService.save(event);

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
        outboxService.save(event);

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
        assertThatThrownBy(() -> outboxService.save(event))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to serialize event payload");
    }

    @Test
    @DisplayName("재시도 가능한 이벤트를 처리할 수 있다.")
    void outboxProcess_WhenCanRetry_success() throws Exception {
        //given
        OutboxEvent event = createOutboxEvent();
        doNothing().when(outboxDispatcher).dispatch(event);

        //when
        OutboxEvent outboxEvent = outboxService.outboxProcess(event);

        //then
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.COMPLETE);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도 불가능한 이벤트는 실패 처리된다.")
    void outboxProcess_WhenCannotRetry_marksFailed() {
        //given
        OutboxEvent event = createOutboxEvent();
        event.incrementRetryCount();
        event.incrementRetryCount();
        event.incrementRetryCount();

        //when
        outboxService.outboxProcess(event);

        //then
        then(kafkaTemplate).should(never()).send(anyString(), anyString());
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getErrorMessage()).isEqualTo("Max retry count exceeded");
    }

    @Test
    @DisplayName("Kafka 발행 실패 시 실패 처리된다.")
    void outboxProcess_WhenKafkaFails_publishFail() {
        //given
        OutboxEvent event = createOutboxEvent();
        doThrow(new RuntimeException("에러")).when(outboxDispatcher).dispatch(event);

        //when
        OutboxEvent outboxEvent = outboxService.outboxProcess(event);

        //then
        then(outboxRepository).should().save(event);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getErrorMessage()).isEqualTo("에러");
    }

    @Test
    @DisplayName("이벤트를 완료 상태로 표시할 수 있다.")
    void markAsProcessed_success() {
        //given
        String eventKey = "test-key";
        OutboxEvent event = createOutboxEvent();
        given(outboxRepository.findByEventKey(eventKey)).willReturn(event);

        //when
        outboxService.markAsProcessed(eventKey);

        //then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.COMPLETE);
    }

    @Test
    @DisplayName("완료된 이벤트를 정리할 수 있다.")
    void cleanUp_success() {
        //given
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        given(outboxRepository.deleteByStatusAndCreatedAtBefore(OutboxStatus.COMPLETE, cutoff))
                .willReturn(10);

        //when
        int deletedCount = outboxService.cleanUp(cutoff);

        //then
        assertThat(deletedCount).isEqualTo(10);
    }

    @Test
    @DisplayName("PENDING 상태의 이벤트 100개를 조회할 수 있다.")
    void findTop100ByStatusOrderByIdAsc_success() {
        //given
        List<OutboxEvent> expectedEvents = List.of(createOutboxEvent());
        given(outboxRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING))
                .willReturn(expectedEvents);

        //when
        List<OutboxEvent> events = outboxService.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING);

        //then
        assertThat(events).hasSize(1);
        assertThat(events).isEqualTo(expectedEvents);
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
