package com.jumunhasyeo.stock.infrastructure.inbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.Idempotency.db.application.DbIdempotentService;
import com.jumunhasyeo.common.Idempotency.db.domain.DbIdempotentKey;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotentType;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.StockService;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.infrastructure.event.KafkaStockCompensationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InboxDispatcherTest {

    @Mock
    private DbIdempotentService dbIdempotentService;

    @Mock
    private StockService stockService;

    @Mock
    private ObjectMapper objectMapper;

    private InboxDispatcher inboxDispatcher;

    @BeforeEach
    void setUp() {
        KafkaStockCompensationService kafkaStockCompensationService = new KafkaStockCompensationService(stockService);
        inboxDispatcher = new InboxDispatcher(dbIdempotentService, kafkaStockCompensationService, objectMapper);
    }

    @Test
    @DisplayName("이미 성공한 중복 보상 재처리는 예외 없이 no-op 처리한다")
    void dispatch_whenSuccessConflict_ignoreDuplicateSuccess() throws Exception {
        InboxEvent event = inboxEvent("event-key");
        String payloadJson = "[{\"productId\":\"550e8400-e29b-41d4-a716-446655440000\",\"amount\":3}]";
        DbIdempotentKey dbIdempotentKey = dbKey("event-key", IdempotentStatus.SUCCESS, payloadJson);
        List<IncreaseStockCommand> payload = List.of(new IncreaseStockCommand(UUID.randomUUID(), 3));

        given(dbIdempotentService.get(event.getEventKey())).willReturn(dbIdempotentKey);
        given(objectMapper.readValue(eq(payloadJson), any(TypeReference.class))).willReturn(payload);
        given(stockService.increment(dbIdempotentKey.genCancelKey(), payload))
                .willThrow(new BusinessException(ErrorCode.SUCCESS_CONFLICT_EXCEPTION));

        assertThatCode(() -> inboxDispatcher.dispatch(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("원본 멱등키가 PROCESSING이면 재시도 예외를 유지한다")
    void dispatch_whenOriginalStatusProcessing_throwRetryableBusinessException() {
        InboxEvent event = inboxEvent("event-key");
        DbIdempotentKey dbIdempotentKey = dbKey("event-key", IdempotentStatus.PROCESSING, "[]");
        given(dbIdempotentService.get(event.getEventKey())).willReturn(dbIdempotentKey);

        assertThatThrownBy(() -> inboxDispatcher.dispatch(event))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROCESSING_CONFLICT_EXCEPTION);
    }

    private static InboxEvent inboxEvent(String eventKey) {
        return InboxEvent.builder()
                .eventKey(eventKey)
                .eventName("OrderCancelEvent")
                .payload("{\"orderId\":\"123\"}")
                .status(InboxStatus.RECEIVED)
                .receivedAt(LocalDateTime.now())
                .retryCount(0)
                .maxRetries(3)
                .build();
    }

    private static DbIdempotentKey dbKey(String idempotencyKey, IdempotentStatus status, String payload) {
        return DbIdempotentKey.builder()
                .idempotencyKey(idempotencyKey)
                .status(status)
                .payload(payload)
                .type(IdempotentType.STOCK)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
    }
}
