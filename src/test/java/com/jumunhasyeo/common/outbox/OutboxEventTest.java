package com.jumunhasyeo.hub.infrastructure.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OutboxEventTest {

    @Test
    @DisplayName("OutboxEvent를 생성할 수 있다.")
    void of_OutboxEvent_success() {
        //given
        String eventName = "HubCreatedEvent";
        String payload = "{\"hubId\":\"123\"}";
        String eventKey = "test-key";

        //when
        OutboxEvent event = OutboxEvent.of(eventName, payload, eventKey, "hub");

        //then
        assertThat(event.getEventName()).isEqualTo(eventName);
        assertThat(event.getPayload()).isEqualTo(payload);
        assertThat(event.getEventKey()).isEqualTo(eventKey);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(0);
        assertThat(event.getMaxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("재시도 횟수를 증가시킬 수 있다.")
    void incrementRetryCount_success() {
        //given
        OutboxEvent event = createOutboxEvent();
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
        OutboxEvent event = createOutboxEvent();

        //when
        boolean canRetry = event.canRetry();

        //then
        assertThat(canRetry).isTrue();
    }

    @Test
    @DisplayName("재시도 횟수가 최대값에 도달하면 재시도 불가능하다.")
    void canRetry_WhenRetryCountEqualsMax_returnsFalse() {
        //given
        OutboxEvent event = createOutboxEvent();
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
    void markProcessed_success() {
        //given
        OutboxEvent event = createOutboxEvent();

        //when
        event.markProcessed();

        //then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.COMPLETE);
    }

    @Test
    @DisplayName("실패 상태로 변경할 수 있다.")
    void markFailed_success() {
        //given
        OutboxEvent event = createOutboxEvent();
        String errorMessage = "Test error";

        //when
        event.markFailed(errorMessage);

        //then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("발행 성공 시 재시도 횟수를 증가하고 완료 상태로 변경한다.")
    void publishSuccess_success() {
        //given
        OutboxEvent event = createOutboxEvent();
        int initialCount = event.getRetryCount();

        //when
        event.publishSuccess();

        //then
        assertThat(event.getRetryCount()).isEqualTo(initialCount + 1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.COMPLETE);
    }

    @Test
    @DisplayName("발행 실패 시 재시도 횟수를 증가하고 에러 메시지를 저장한다.")
    void publishFail_success() {
        //given
        OutboxEvent event = createOutboxEvent();
        int initialCount = event.getRetryCount();
        String errorMessage = "Publish failed";

        //when
        event.publishFail(errorMessage);

        //then
        assertThat(event.getRetryCount()).isEqualTo(initialCount + 1);
        assertThat(event.getErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("에러 메시지를 설정할 수 있다.")
    void setErrorMessage_success() {
        //given
        OutboxEvent event = createOutboxEvent();
        String errorMessage = "Custom error message";

        //when
        event.setErrorMessage(errorMessage);

        //then
        assertThat(event.getErrorMessage()).isEqualTo(errorMessage);
    }

    private static OutboxEvent createOutboxEvent() {
        return OutboxEvent.of(
                "HubCreatedEvent",
                "{\"hubId\":\"123\"}",
                "test-key",
                "hub"
        );
    }
}
