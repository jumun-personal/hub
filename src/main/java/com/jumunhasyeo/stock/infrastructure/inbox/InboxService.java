package com.jumunhasyeo.stock.infrastructure.inbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.infrastructure.event.OrderCancelEvent;
import com.jumunhasyeo.stock.infrastructure.event.OrderCompensationEvent;
import com.jumunhasyeo.stock.infrastructure.event.OrderRolledBackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.jumunhasyeo.stock.infrastructure.event.ListenEventRegistry.ORDER_CANCEL_EVENT;
import static com.jumunhasyeo.stock.infrastructure.event.ListenEventRegistry.ORDER_ROLLED_BACK_EVENT;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboxService {
    private static final Set<ErrorCode> RETRYABLE_BUSINESS_ERRORS = Set.of(
            ErrorCode.PROCESSING_CONFLICT_EXCEPTION,
            ErrorCode.INTERNAL_SERVER_ERROR
    );

    private final InboxRepository inboxRepository;
    private final InboxDispatcher inboxDispatcher;
    private final ObjectMapper objectMapper;

    public void save(OrderCompensationEvent event) throws JsonProcessingException {
        if (inboxRepository.existsByEventKey(event.getKey())) {
            log.info("Inbox event already exists. skip save. eventKey={}", event.getKey());
            return;
        }

        InboxEvent inboxEvent = InboxEvent.builder()
                .eventKey(event.getKey())
                .eventName(resolveEventName(event))
                .payload(objectMapper.writeValueAsString(event))
                .status(InboxStatus.RECEIVED)
                .receivedAt(LocalDateTime.now())
                .build();
        inboxRepository.save(inboxEvent);
    }

    public List<InboxEvent> findByStatusAndModifiedAtBefore(InboxStatus inboxStatus, LocalDateTime threshold) {
        return inboxRepository.findByStatusAndModifiedAtBefore(inboxStatus, threshold);
    }

    @Transactional
    public void inboxProcess(InboxEvent event) {
        if (!event.canRetry()) {
            event.markFailed("Max retry count exceeded");
            inboxRepository.save(event);
            return;
        }

        event.markProcessing();
        inboxRepository.save(event);

        try {
            inboxDispatcher.dispatch(event);
            event.dispatchSuccess();
        } catch (BusinessException e) {
            if (RETRYABLE_BUSINESS_ERRORS.contains(e.getErrorCode())) {
                event.dispatchFail(e.getMessage());
            } else {
                event.markFailed(e.getMessage());
            }
        } catch (Exception e) {
            event.dispatchFail(e.getMessage());
        }
        inboxRepository.save(event);
    }

    private String resolveEventName(OrderCompensationEvent event) {
        if (event instanceof OrderCancelEvent) {
            return ORDER_CANCEL_EVENT.getEventName();
        }
        if (event instanceof OrderRolledBackEvent) {
            return ORDER_ROLLED_BACK_EVENT.getEventName();
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 보상 이벤트 타입입니다. type=" + event.getClass().getName());
    }
}
