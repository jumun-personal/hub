package com.jumunhasyeo.stock.infrastructure.event;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.domain.entity.StockHistory;
import com.jumunhasyeo.stock.domain.repository.StockHistoryRepository;
import com.jumunhasyeo.stock.infrastructure.inbox.InboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jumunhasyeo.stock.domain.entity.StockHistory.StockHistoryType.DECREASE;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrderCompensateHandlerTest {

    @Mock
    private com.jumunhasyeo.stock.application.StockService stockService;

    @Mock
    private StockHistoryRepository stockHistoryRepository;

    @Mock
    private InboxService inboxService;

    private OrderCompensateHandler orderCompensateHandler;

    @BeforeEach
    void setUp() {
        KafkaStockCompensationService kafkaStockCompensationService = new KafkaStockCompensationService(stockService);
        orderCompensateHandler = new OrderCompensateHandler(
                kafkaStockCompensationService,
                stockHistoryRepository,
                inboxService
        );
    }

    @Test
    @DisplayName("원본 차감 이력이 없으면 inbox에 적재한다")
    void compensate_whenDecreaseHistoryNotFound_enqueueInbox() throws Exception {
        OrderCancelEvent event = new OrderCancelEvent(UUID.randomUUID(), "", LocalDateTime.now());
        given(stockHistoryRepository.findByIdempotencyKeyAndType(event.getKey(), DECREASE)).willReturn(List.of());

        orderCompensateHandler.compensate(event);

        then(inboxService).should().save(event);
        then(stockService).should(never()).increment(any(), any());
    }

    @Test
    @DisplayName("원본 차감 이력이 있으면 증가 보상을 실행한다")
    void compensate_whenDecreaseHistoryExists_incrementStock() throws Exception {
        OrderCancelEvent event = new OrderCancelEvent(UUID.randomUUID(), "", LocalDateTime.now());
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        StockHistory history = StockHistory.ofDecrease(hubId, productId, 3, event.getKey());
        given(stockHistoryRepository.findByIdempotencyKeyAndType(event.getKey(), DECREASE)).willReturn(List.of(history));

        orderCompensateHandler.compensate(event);

        then(stockService).should().increment(eq("CANCEL_" + event.getKey()), argThat(payload ->
                payload.size() == 1
                        && payload.get(0).productId().equals(productId)
                        && payload.get(0).amount() == 3
        ));
        then(inboxService).should(never()).save(any());
    }

    @Test
    @DisplayName("보상 increment가 이미 처리 중이거나 성공한 중복이면 예외 없이 no-op 처리한다")
    void compensate_whenConflict_ignoreDuplicate() throws Exception {
        OrderCancelEvent event = new OrderCancelEvent(UUID.randomUUID(), "", LocalDateTime.now());
        UUID productId = UUID.randomUUID();
        StockHistory history = StockHistory.ofDecrease(UUID.randomUUID(), productId, 3, event.getKey());
        given(stockHistoryRepository.findByIdempotencyKeyAndType(event.getKey(), DECREASE)).willReturn(List.of(history));
        given(stockService.increment(eq("CANCEL_" + event.getKey()), any()))
                .willThrow(new BusinessException(ErrorCode.PROCESSING_CONFLICT_EXCEPTION));

        assertThatCode(() -> orderCompensateHandler.compensate(event))
                .doesNotThrowAnyException();

        then(inboxService).should(never()).save(any());
    }
}
