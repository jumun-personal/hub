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

    @Transactional
    public void publishAfterCommit(String eventKey) {
        OutboxEvent outboxEvent = outboxRepository.findByEventKey(eventKey);
        outboxProcess(outboxEvent);
    }

    @Transactional
    public void markAsProcessed(String eventKey) {
        OutboxEvent outboxEvent = outboxRepository.findByEventKey(eventKey);
        outboxEvent.markProcessed();
    }

    @Transactional
    public OutboxEvent outboxProcess(OutboxEvent event) {
        try {
            if (!event.canRetry()) {
                event.markFailed("Max retry count exceeded");
                outboxRepository.save(event);
                return event;
            }

            outboxDispatcher.dispatch(event);
            event.publishSuccess();
            outboxRepository.save(event);
            return event;
        } catch (Exception e) {
            event.publishFail(e.getMessage());
            outboxRepository.save(event);
            return event;
        }
    }

    @Transactional
    public void processFailedEventsWithLock() {
        List<OutboxEvent> failedEvents = outboxRepository.findTop100ByStatusForUpdateSkipLocked(OutboxStatus.FAILED);
        for (OutboxEvent event : failedEvents) {
            outboxProcess(event);
        }
    }

    @Transactional
    public void processPendingEventsWithLock(LocalDateTime pendingCutoff) {
        List<OutboxEvent> pendingEvents = outboxRepository.findTop100ByStatusAndCreatedAtBeforeForUpdateSkipLocked(
                OutboxStatus.PENDING,
                pendingCutoff
        );
        for (OutboxEvent event : pendingEvents) {
            outboxProcess(event);
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
