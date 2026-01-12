package com.jumunhasyeo.hub.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubNameUpdatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubUpdatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildCompletedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildFailedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteCreatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.jumunhasyeo.hub.hubRoute.domain.event.PublishEventRegistry.HUB_ROUTE_CREATED_EVENT;
import static com.jumunhasyeo.hub.hubRoute.domain.event.PublishEventRegistry.HUB_ROUTE_DELETED_EVENT;
import static com.jumunhasyeo.hub.hubRoute.domain.event.PublishEventRegistry.HUB_ROUTE_BUILD_COMPLETED_EVENT;
import static com.jumunhasyeo.hub.hubRoute.domain.event.PublishEventRegistry.HUB_ROUTE_BUILD_FAILED_EVENT;
import static com.jumunhasyeo.hub.hubRoute.infrastructure.event.ListenEventRegistry.HUB_CREATED_EVENT;

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
    public void save(HubNameUpdatedEvent event) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.of("HubNameUpdatedEvent", objectMapper.writeValueAsString(event), event.getEventKey(), hubTopic);
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize event payload");
        }
    }

    @Transactional
    public void save(HubDeletedEvent event) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.of("HubDeletedEvent", objectMapper.writeValueAsString(event), event.getEventKey(), hubTopic);
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize event payload");
        }
    }

    @Transactional
    public void save(HubCreatedEvent event) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.of(HUB_CREATED_EVENT.getEventName(), objectMapper.writeValueAsString(event), event.getEventKey(), hubTopic);
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize event payload");
        }
    }

    @Transactional
    public void save(HubRouteCreatedEvent event) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.of(HUB_ROUTE_CREATED_EVENT.getEventName(), objectMapper.writeValueAsString(event), event.getEventKey(), hubTopic);
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize event payload");
        }
    }

    @Transactional
    public void save(HubRouteDeletedEvent event) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.of(HUB_ROUTE_DELETED_EVENT.getEventName(), objectMapper.writeValueAsString(event), event.getEventKey(), hubTopic);
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize event payload");
        }
    }

    @Transactional
    public void save(HubUpdatedEvent event) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.of("HubUpdatedEvent", objectMapper.writeValueAsString(event), event.getEventKey(), hubTopic);
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize event payload");
        }
    }

    @Transactional
    public void save(HubRouteBuildCompletedEvent event) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.of(HUB_ROUTE_BUILD_COMPLETED_EVENT.getEventName(), objectMapper.writeValueAsString(event), event.getEventKey(), hubTopic);
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize event payload");
        }
    }

    @Transactional
    public void save(HubRouteBuildFailedEvent event) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.of(HUB_ROUTE_BUILD_FAILED_EVENT.getEventName(), objectMapper.writeValueAsString(event), event.getEventKey(), hubTopic);
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
