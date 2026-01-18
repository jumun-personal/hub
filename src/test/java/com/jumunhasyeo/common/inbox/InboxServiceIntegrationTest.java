package com.jumunhasyeo.common.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.stock.infrastructure.event.OrderCancelEvent;
import com.jumunhasyeo.stock.infrastructure.inbox.InboxEvent;
import com.jumunhasyeo.stock.infrastructure.inbox.InboxService;
import com.jumunhasyeo.stock.infrastructure.inbox.InboxStatus;
import com.jumunhasyeo.stock.infrastructure.inbox.JpaInboxRepository;
import com.jumunhasyeo.testsupport.AbstractEventDispatchIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

public class InboxServiceIntegrationTest extends AbstractEventDispatchIntegrationTest {

    @Autowired
    private InboxService inboxService;

    @Autowired
    private JpaInboxRepository jpaInboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("OrderCancelEvent를 저장하고 조회할 수 있다.")
    void save_OrderCancelEvent_integration_success() throws Exception {
        //given
        OrderCancelEvent event = new OrderCancelEvent(UUID.randomUUID(), "", LocalDateTime.now());

        //when
        inboxService.save(event);

        //then
        List<InboxEvent> events = jpaInboxRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventKey()).isEqualTo(event.getKey());
        assertThat(events.get(0).getStatus()).isEqualTo(InboxStatus.RECEIVED);
    }

    @Test
    @DisplayName("PROCESSING 상태의 이벤트를 조회할 수 있다.")
    void findByStatusAndModifiedAtBefore_integration_success() {
        //given
        InboxEvent event = InboxEvent.builder()
                .eventKey("test-key")
                .eventName("ORDER_CANCEL_EVENT")
                .payload("{\"orderId\":\"123\"}")
                .status(InboxStatus.PROCESSING)
                .receivedAt(LocalDateTime.now())
                .build();
        jpaInboxRepository.save(event);

        LocalDateTime threshold = LocalDateTime.now().plusMinutes(1);

        //when
        List<InboxEvent> events = inboxService.findByStatusAndModifiedAtBefore(
                InboxStatus.PROCESSING, threshold
        );

        //then
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventKey()).isEqualTo("test-key");
    }

    @Test
    @DisplayName("재시도 가능한 이벤트를 처리하면 COMPLETED 상태가 된다.")
    void inboxProcess_integration_success() {
        doNothing().when(inboxDispatcher).dispatch(any());
        //given
        InboxEvent event = InboxEvent.builder()
                .eventKey("test-key")
                .eventName("ORDER_CANCEL_EVENT")
                .payload("{\"orderId\":\"123\"}")
                .status(InboxStatus.RECEIVED)
                .receivedAt(LocalDateTime.now())
                .retryCount(0)
                .maxRetries(3)
                .build();
        jpaInboxRepository.save(event);

        //when
        inboxService.inboxProcess(event);

        //then
        InboxEvent savedEvent = jpaInboxRepository.findByEventKey("test-key").orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(InboxStatus.COMPLETED);
        assertThat(savedEvent.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도 불가능한 이벤트는 FAILED 상태가 된다.")
    void inboxProcess_WhenCannotRetry_integration_marksFailed() {
        doNothing().when(inboxDispatcher).dispatch(any());
        //given
        InboxEvent event = InboxEvent.builder()
                .eventKey("test-key-2")
                .eventName("ORDER_CANCEL_EVENT")
                .payload("{\"orderId\":\"123\"}")
                .status(InboxStatus.RECEIVED)
                .receivedAt(LocalDateTime.now())
                .retryCount(3)
                .maxRetries(3)
                .build();
        jpaInboxRepository.save(event);

        //when
        inboxService.inboxProcess(event);

        //then
        InboxEvent savedEvent = jpaInboxRepository.findByEventKey("test-key-2").orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(InboxStatus.FAILED);
        assertThat(savedEvent.getErrorMessage()).isEqualTo("Max retry count exceeded");
    }
}
