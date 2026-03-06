package com.jumunhasyeo.hub.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 저장부터 클레임, 발행 결과 전이, 재처리와 정리까지 소유하는 publication Module.
 */
@Service
@RequiredArgsConstructor
public class OutboxPublicationService {

    @Value("${spring.kafka.topics.hub}")
    private String hubTopic;

    private final JpaOutboxRepository outboxRepository;
    private final OutboxStateTransitions outboxStateTransitions;
    private final OutboxDispatcher outboxDispatcher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void append(OutboxMessage event) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.of(
                    event.eventName(),
                    objectMapper.writeValueAsString(event),
                    event.eventKey(),
                    topicFor(event)
            );
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize event payload");
        }
    }

    public void publishAfterCommit(String eventKey) {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(5);
        outboxStateTransitions.claimByEventKey(eventKey, staleBefore)
                .ifPresent(this::publishClaimedEvent);
    }

    public void publishPending(LocalDateTime staleBefore) {
        List<OutboxEvent> events = outboxStateTransitions.claimPublishableEvents(staleBefore);
        events.forEach(this::publishClaimedEvent);
    }

    @Transactional
    public int cleanupCompletedBefore(LocalDateTime cutoff) {
        return outboxRepository.deleteByStatusAndCreatedAtBefore(OutboxStatus.COMPLETE, cutoff);
    }

    OutboxEvent publishClaimedEvent(OutboxEvent event) {
        if (!event.canRetry()) {
            outboxStateTransitions.markPublishDead(event, "Max retry count exceeded");
            return event;
        }

        try {
            outboxDispatcher.dispatch(event);
            outboxStateTransitions.markPublishSuccess(event);
        } catch (Exception e) {
            outboxStateTransitions.markPublishFailure(event, e.getMessage());
        }
        return event;
    }

    private String topicFor(OutboxMessage event) {
        return hubTopic;
    }
}
