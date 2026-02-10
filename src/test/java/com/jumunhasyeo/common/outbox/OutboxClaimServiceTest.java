package com.jumunhasyeo.hub.infrastructure.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OutboxClaimServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private OutboxClaimService outboxClaimService;

    @Test
    @DisplayName("eventKey로 클레임하면 이벤트가 PROCESSING 상태가 된다.")
    void claimByEventKey_marksProcessing() {
        // given
        String eventKey = "event-key";
        LocalDateTime staleBefore = LocalDateTime.of(2026, 6, 25, 10, 0);
        OutboxEvent event = createOutboxEvent(eventKey);
        given(outboxRepository.findClaimableByEventKeyForUpdateSkipLocked(eventKey, staleBefore))
                .willReturn(Optional.of(event));

        // when
        Optional<OutboxEvent> result = outboxClaimService.claimByEventKey(eventKey, staleBefore);

        // then
        assertThat(result).contains(event);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(event.getClaimedAt()).isNotNull();
        then(outboxRepository).should().save(event);
    }

    @Test
    @DisplayName("발행 가능한 이벤트 배치를 클레임하면 모두 PROCESSING 상태가 된다.")
    void claimPublishableEvents_marksProcessing() {
        // given
        LocalDateTime staleBefore = LocalDateTime.of(2026, 6, 25, 10, 0);
        OutboxEvent event1 = createOutboxEvent("event-key-1");
        OutboxEvent event2 = createOutboxEvent("event-key-2");
        given(outboxRepository.findTop100ClaimableForUpdateSkipLocked(staleBefore))
                .willReturn(List.of(event1, event2));

        // when
        List<OutboxEvent> result = outboxClaimService.claimPublishableEvents(staleBefore);

        // then
        assertThat(result).containsExactly(event1, event2);
        assertThat(event1.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(event2.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        then(outboxRepository).should().save(event1);
        then(outboxRepository).should().save(event2);
    }

    @Test
    @DisplayName("발행 성공을 기록하면 이벤트가 COMPLETE 상태가 된다.")
    void markPublishSuccess_marksComplete() {
        // given
        OutboxEvent event = createOutboxEvent("event-key");
        event.claimProcessing();

        // when
        outboxClaimService.markPublishSuccess(event);

        // then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.COMPLETE);
        assertThat(event.getProcessedAt()).isNotNull();
        then(outboxRepository).should().save(event);
    }

    @Test
    @DisplayName("발행 실패가 재시도 가능하면 이벤트가 FAILED 상태가 된다.")
    void markPublishFailure_whenCanRetry_marksFailed() {
        // given
        OutboxEvent event = createOutboxEvent("event-key");

        // when
        outboxClaimService.markPublishFailure(event, "publish failed");

        // then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(1);
        then(outboxRepository).should().save(event);
    }

    @Test
    @DisplayName("발행 실패가 최대 재시도에 도달하면 이벤트가 DEAD 상태가 된다.")
    void markPublishFailure_whenMaxRetryReached_marksDead() {
        // given
        OutboxEvent event = createOutboxEvent("event-key");
        event.incrementRetryCount();
        event.incrementRetryCount();

        // when
        outboxClaimService.markPublishFailure(event, "publish failed");

        // then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(event.getRetryCount()).isEqualTo(3);
        then(outboxRepository).should().save(event);
    }

    private static OutboxEvent createOutboxEvent(String eventKey) {
        return OutboxEvent.of(
                "HubCreatedEvent",
                "{\"hubId\":\"123\"}",
                eventKey,
                "hub"
        );
    }
}
