package com.jumunhasyeo.stock.infrastructure.event;

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
import com.jumunhasyeo.stock.infrastructure.inbox.InboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrderCompensateHandlerTest {

    @Mock
    private StockService stockService;

    @Mock
    private DbIdempotentService dbIdempotentService;

    @Mock
    private InboxService inboxService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderCompensateHandler orderCompensateHandler;

    @Test
    @DisplayName("idempotent key가 없으면 inbox에 적재한다")
    void compensate_whenIdempotentKeyNotFound_enqueueInbox() throws Exception {
        OrderCancelEvent event = new OrderCancelEvent(UUID.randomUUID(), "", LocalDateTime.now());
        given(dbIdempotentService.get(event.getKey())).willReturn(null);

        orderCompensateHandler.compensate(event);

        then(inboxService).should().save(event);
        then(stockService).should(never()).increment(any(), any());
    }

    @Test
    @DisplayName("idempotent status가 PROCESSING이면 inbox에 적재한다")
    void compensate_whenProcessing_enqueueInbox() throws Exception {
        OrderCancelEvent event = new OrderCancelEvent(UUID.randomUUID(), "", LocalDateTime.now());
        DbIdempotentKey key = dbKey(event.getKey(), IdempotentStatus.PROCESSING, "[]");
        given(dbIdempotentService.get(event.getKey())).willReturn(key);

        orderCompensateHandler.compensate(event);

        then(inboxService).should().save(event);
        then(stockService).should(never()).increment(any(), any());
    }

    @Test
    @DisplayName("idempotent status가 SUCCESS면 즉시 보상 처리한다")
    void compensate_whenSuccess_incrementStock() throws Exception {
        OrderCancelEvent event = new OrderCancelEvent(UUID.randomUUID(), "", LocalDateTime.now());
        String payloadJson = "[{\"productId\":\"550e8400-e29b-41d4-a716-446655440000\",\"amount\":3}]";
        DbIdempotentKey key = dbKey(event.getKey(), IdempotentStatus.SUCCESS, payloadJson);
        List<IncreaseStockCommand> payload = List.of(new IncreaseStockCommand(UUID.randomUUID(), 3));

        given(dbIdempotentService.get(event.getKey())).willReturn(key);
        given(objectMapper.readValue(eq(payloadJson), any(TypeReference.class))).willReturn(payload);

        orderCompensateHandler.compensate(event);

        then(stockService).should().increment(eq(key.genCancelKey()), eq(payload));
        then(inboxService).should(never()).save(any());
    }

    @Test
    @DisplayName("idempotent status가 비성공/비처리중이면 예외를 던진다")
    void compensate_whenInvalidStatus_throwException() throws Exception {
        OrderCancelEvent event = new OrderCancelEvent(UUID.randomUUID(), "", LocalDateTime.now());
        DbIdempotentKey key = dbKey(event.getKey(), IdempotentStatus.FAIL, "[]");
        given(dbIdempotentService.get(event.getKey())).willReturn(key);

        assertThatThrownBy(() -> orderCompensateHandler.compensate(event))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);

        then(inboxService).should(never()).save(any());
        then(stockService).should(never()).increment(any(), any());
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
