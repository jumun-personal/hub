package com.jumunhasyeo.hub.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {
    @Value("${spring.kafka.topics.hub}")
    private String hubTopic;
    private final OutboxRepository outboxRepository;
    private final OutboxClaimService outboxClaimService;
    private final OutboxDispatcher outboxDispatcher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(OutboxMessage event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.of(event.eventName(), payload, event.eventKey(), hubTopic);
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize event payload");
        }
    }

    public void publishAfterCommit(String eventKey) {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(5);
        outboxClaimService.claimByEventKey(eventKey, staleBefore)
                .ifPresent(this::publishClaimedEvent);
    }

    @Transactional
    public void markAsProcessed(String eventKey) {
        OutboxEvent outboxEvent = outboxRepository.findByEventKey(eventKey);
        outboxEvent.markProcessed();
    }

    public OutboxEvent publishClaimedEvent(OutboxEvent event) {
        try {
            if (!event.canRetry()) {
                event.markDead("Max retry count exceeded");
                outboxRepository.save(event);
                return event;
            }

            outboxDispatcher.dispatch(event);
            outboxClaimService.markPublishSuccess(event);
            return event;
        } catch (Exception e) {
            outboxClaimService.markPublishFailure(event, e.getMessage());
            return event;
        }
    }

    public void processClaimableEvents(LocalDateTime staleBefore) {
        List<OutboxEvent> events = outboxClaimService.claimPublishableEvents(staleBefore);
        for (OutboxEvent event : events) {
            publishClaimedEvent(event);
        }
    }

    public int cleanUp(LocalDateTime cutoff) {
        return outboxRepository.deleteByStatusAndCreatedAtBefore(
                OutboxStatus.COMPLETE, cutoff
        );
    }

    public List<OutboxEvent> findTop100ByStatusOrderByIdAsc(OutboxStatus outboxStatus) {
        return outboxRepository.findTop100ByStatusOrderByIdAsc(outboxStatus);
    }

    public List<OutboxEvent> findTop100ByStatusAndCreatedAtBeforeOrderByIdAsc(OutboxStatus outboxStatus, LocalDateTime createdAt) {
        return outboxRepository.findTop100ByStatusAndCreatedAtBeforeOrderByIdAsc(outboxStatus, createdAt);
    }
}
