package com.jumunhasyeo.stock.infrastructure.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.infrastructure.event.OrderCancelEvent;
import com.jumunhasyeo.stock.infrastructure.event.OrderRolledBackEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;
import static com.jumunhasyeo.stock.infrastructure.event.ListenEventRegistry.ORDER_CANCEL_EVENT;
import static com.jumunhasyeo.stock.infrastructure.event.ListenEventRegistry.ORDER_ROLLED_BACK_EVENT;

@ExtendWith(MockitoExtension.class)
public class InboxServiceTest {

    @Mock
    private InboxRepository inboxRepository;

    @Mock
    private InboxDispatcher inboxDispatcher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private InboxService inboxService;

    @Test
    @DisplayName("OrderCancelEvent를 저장할 수 있다.")
    void save_OrderCancelEvent_success() throws Exception {
        //given
        OrderCancelEvent event = new OrderCancelEvent(UUID.randomUUID(), "", LocalDateTime.now());
        String expectedJson = "{\"key\":\"test-key\"}";
        given(objectMapper.writeValueAsString(event)).willReturn(expectedJson);

        //when
        inboxService.save(event);

        //then
        ArgumentCaptor<InboxEvent> captor = ArgumentCaptor.forClass(InboxEvent.class);
        then(inboxRepository).should().save(captor.capture());

        InboxEvent savedEvent = captor.getValue();
        assertThat(savedEvent.getEventKey()).isEqualTo(event.getKey());
        assertThat(savedEvent.getEventName()).isEqualTo(ORDER_CANCEL_EVENT.getEventName());
        assertThat(savedEvent.getPayload()).isEqualTo(expectedJson);
        assertThat(savedEvent.getStatus()).isEqualTo(InboxStatus.RECEIVED);
    }

    @Test
    @DisplayName("OrderRolledBackEvent를 저장할 수 있다.")
    void save_OrderRolledBackEvent_success() throws Exception {
        //given
        OrderRolledBackEvent event = new OrderRolledBackEvent(UUID.randomUUID(), "ROLLED_BACK", LocalDateTime.now());
        String expectedJson = "{\"key\":\"test-key\"}";
        given(objectMapper.writeValueAsString(event)).willReturn(expectedJson);

        //when
        inboxService.save(event);

        //then
        ArgumentCaptor<InboxEvent> captor = ArgumentCaptor.forClass(InboxEvent.class);
        then(inboxRepository).should().save(captor.capture());

        InboxEvent savedEvent = captor.getValue();
        assertThat(savedEvent.getEventKey()).isEqualTo(event.getKey());
        assertThat(savedEvent.getEventName()).isEqualTo(ORDER_ROLLED_BACK_EVENT.getEventName());
        assertThat(savedEvent.getPayload()).isEqualTo(expectedJson);
        assertThat(savedEvent.getStatus()).isEqualTo(InboxStatus.RECEIVED);
    }

    @Test
    @DisplayName("상태와 수정시간으로 InboxEvent를 조회할 수 있다.")
    void findByStatusAndModifiedAtBefore_success() {
        //given
        InboxStatus status = InboxStatus.PROCESSING;
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(30);
        List<InboxEvent> expectedEvents = List.of(createInboxEvent());
        given(inboxRepository.findByStatusAndModifiedAtBefore(status, threshold))
                .willReturn(expectedEvents);

        //when
        List<InboxEvent> events = inboxService.findByStatusAndModifiedAtBefore(status, threshold);

        //then
        assertThat(events).hasSize(1);
        assertThat(events).isEqualTo(expectedEvents);
    }

    @Test
    @DisplayName("재시도 가능한 이벤트를 처리할 수 있다.")
    void inboxProcess_WhenCanRetry_success() {
        //given
        InboxEvent event = createInboxEvent();
        doNothing().when(inboxDispatcher).dispatch(event);

        //when
        inboxService.inboxProcess(event);

        //then
        then(inboxDispatcher).should().dispatch(event);
        assertThat(event.getStatus()).isEqualTo(InboxStatus.COMPLETED);
        assertThat(event.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도 불가능한 이벤트는 실패 처리된다.")
    void inboxProcess_WhenCannotRetry_marksFailed() {
        //given
        InboxEvent event = createInboxEvent();
        event.incrementRetryCount();
        event.incrementRetryCount();
        event.incrementRetryCount();

        //when
        inboxService.inboxProcess(event);

        //then
        then(inboxDispatcher).should(never()).dispatch(any());
        assertThat(event.getStatus()).isEqualTo(InboxStatus.FAILED);
        assertThat(event.getErrorMessage()).isEqualTo("Max retry count exceeded");
    }

    @Test
    @DisplayName("처리 중 예외 발생 시 실패 처리된다.")
    void inboxProcess_WhenException_dispatchFail() {
        //given
        InboxEvent event = createInboxEvent();
        RuntimeException exception = new RuntimeException("Dispatch error");
        doThrow(exception).when(inboxDispatcher).dispatch(event);

        //when
        inboxService.inboxProcess(event);

        //then
        then(inboxRepository).should(times(2)).save(event);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getErrorMessage()).isEqualTo("Dispatch error");
        assertThat(event.getStatus()).isEqualTo(InboxStatus.RECEIVED);
    }

    @Test
    @DisplayName("재시도 불가능한 BusinessException 발생 시 FAILED 처리된다.")
    void inboxProcess_WhenNonRetryableBusinessException_marksFailed() {
        //given
        InboxEvent event = createInboxEvent();
        doThrow(new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 이벤트"))
                .when(inboxDispatcher).dispatch(event);

        //when
        inboxService.inboxProcess(event);

        //then
        then(inboxRepository).should(times(2)).save(event);
        assertThat(event.getStatus()).isEqualTo(InboxStatus.FAILED);
        assertThat(event.getErrorMessage()).contains("지원하지 않는 이벤트");
    }

    private static InboxEvent createInboxEvent() {
        return InboxEvent.builder()
                .eventKey("test-key")
                .eventName("ORDER_CANCEL_EVENT")
                .payload("{\"orderId\":\"123\"}")
                .status(InboxStatus.RECEIVED)
                .receivedAt(LocalDateTime.now())
                .retryCount(0)
                .maxRetries(3)
                .build();
    }
}
