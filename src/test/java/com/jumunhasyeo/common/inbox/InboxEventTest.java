package com.jumunhasyeo.stock.infrastructure.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class InboxEventTest {

    @Test
    @DisplayName("InboxEvent를 생성할 수 있다.")
    void from_InboxEvent_success() {
        //given
        String eventKey = "test-key";
        String eventName = "ORDER_CANCEL_EVENT";
        String payload = "{\"orderId\":\"123\"}";

        //when
        InboxEvent event = InboxEvent.from(eventKey, eventName, payload);

        //then
        assertThat(event.getEventKey()).isEqualTo(eventKey);
        assertThat(event.getEventName()).isEqualTo(eventName);
        assertThat(event.getPayload()).isEqualTo(payload);
        assertThat(event.getStatus()).isEqualTo(InboxStatus.RECEIVED);
        assertThat(event.getReceivedAt()).isNotNull();
    }

    @Test
    @DisplayName("재시도 횟수를 증가시킬 수 있다.")
    void incrementRetryCount_success() {
        //given
        InboxEvent event = createInboxEvent();
        int initialCount = event.getRetryCount();

        //when
        event.incrementRetryCount();

        //then
        assertThat(event.getRetryCount()).isEqualTo(initialCount + 1);
    }

    @Test
    @DisplayName("재시도 가능 여부를 확인할 수 있다.")
    void canRetry_WhenRetryCountLessThanMax_returnsTrue() {
        //given
        InboxEvent event = createInboxEvent();

        //when
        boolean canRetry = event.canRetry();

        //then
        assertThat(canRetry).isTrue();
    }

    @Test
    @DisplayName("재시도 횟수가 최대값에 도달하면 재시도 불가능하다.")
    void canRetry_WhenRetryCountEqualsMax_returnsFalse() {
        //given
        InboxEvent event = createInboxEvent();
        for (int i = 0; i < 3; i++) {
            event.incrementRetryCount();
        }

        //when
        boolean canRetry = event.canRetry();

        //then
        assertThat(canRetry).isFalse();
    }

    @Test
    @DisplayName("완료 상태로 변경할 수 있다.")
    void markCompleted_success() {
        //given
        InboxEvent event = createInboxEvent();

        //when
        event.markCompleted();

        //then
        assertThat(event.getStatus()).isEqualTo(InboxStatus.COMPLETED);
        assertThat(event.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("실패 상태로 변경할 수 있다.")
    void markFailed_success() {
        //given
        InboxEvent event = createInboxEvent();
        String errorMessage = "Test error";

        //when
        event.markFailed(errorMessage);

        //then
        assertThat(event.getStatus()).isEqualTo(InboxStatus.FAILED);
        assertThat(event.getErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("발송 성공 시 재시도 횟수를 증가하고 완료 상태로 변경한다.")
    void dispatchSuccess_success() {
        //given
        InboxEvent event = createInboxEvent();
        int initialCount = event.getRetryCount();

        //when
        event.dispatchSuccess();

        //then
        assertThat(event.getRetryCount()).isEqualTo(initialCount + 1);
        assertThat(event.getStatus()).isEqualTo(InboxStatus.COMPLETED);
        assertThat(event.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("발송 실패 시 재시도 가능하면 RECEIVED 상태로 되돌린다.")
    void dispatchFail_WhenCanRetry_marksReceived() {
        //given
        InboxEvent event = createInboxEvent();
        int initialCount = event.getRetryCount();
        String errorMessage = "Dispatch failed";

        //when
        event.dispatchFail(errorMessage);

        //then
        assertThat(event.getRetryCount()).isEqualTo(initialCount + 1);
        assertThat(event.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(event.getStatus()).isEqualTo(InboxStatus.RECEIVED);
    }

    @Test
    @DisplayName("발송 실패 시 재시도 횟수 초과면 FAILED 상태가 된다.")
    void dispatchFail_WhenRetryExceeded_marksFailed() {
        //given
        InboxEvent event = createInboxEvent();
        event.incrementRetryCount();
        event.incrementRetryCount();
        String errorMessage = "Dispatch failed";

        //when
        event.dispatchFail(errorMessage);

        //then
        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(event.getStatus()).isEqualTo(InboxStatus.FAILED);
    }

    @Test
    @DisplayName("상태를 업데이트할 수 있다.")
    void updateStatus_success() {
        //given
        InboxEvent event = createInboxEvent();

        //when
        event.updateStatus(InboxStatus.PROCESSING);

        //then
        assertThat(event.getStatus()).isEqualTo(InboxStatus.PROCESSING);
    }

    @Test
    @DisplayName("상태를 COMPLETED로 변경하면 processedAt이 설정된다.")
    void updateStatus_WhenCompleted_setsProcessedAt() {
        //given
        InboxEvent event = createInboxEvent();

        //when
        event.updateStatus(InboxStatus.COMPLETED);

        //then
        assertThat(event.getStatus()).isEqualTo(InboxStatus.COMPLETED);
        assertThat(event.getProcessedAt()).isNotNull();
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
