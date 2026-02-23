package com.jumunhasyeo.stock.infrastructure.inbox;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.StockService;
import com.jumunhasyeo.stock.domain.entity.StockHistory;
import com.jumunhasyeo.stock.domain.repository.StockHistoryRepository;
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

import static com.jumunhasyeo.stock.domain.entity.StockHistory.StockHistoryType.DECREASE;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InboxDispatcherTest {

    @Mock
    private StockHistoryRepository stockHistoryRepository;

    @Mock
    private StockService stockService;

    private InboxDispatcher inboxDispatcher;

    @BeforeEach
    void setUp() {
        KafkaStockCompensationService kafkaStockCompensationService = new KafkaStockCompensationService(stockService);
        inboxDispatcher = new InboxDispatcher(stockHistoryRepository, kafkaStockCompensationService);
    }

    @Test
    @DisplayName("이미 성공한 중복 보상 재처리는 예외 없이 no-op 처리한다")
    void dispatch_whenSuccessConflict_ignoreDuplicate() {
        InboxEvent event = inboxEvent("event-key");
        StockHistory history = StockHistory.ofDecrease(UUID.randomUUID(), UUID.randomUUID(), 3, event.getEventKey());
        given(stockHistoryRepository.findByIdempotencyKeyAndType(event.getEventKey(), DECREASE)).willReturn(List.of(history));
        given(stockService.increment(eq("CANCEL_" + event.getEventKey()), any()))
                .willThrow(new BusinessException(ErrorCode.SUCCESS_CONFLICT_EXCEPTION));

        assertThatCode(() -> inboxDispatcher.dispatch(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("원본 차감 이력이 없으면 재시도 예외를 유지한다")
    void dispatch_whenDecreaseHistoryMissing_throwRetryableBusinessException() {
        InboxEvent event = inboxEvent("event-key");
        given(stockHistoryRepository.findByIdempotencyKeyAndType(event.getEventKey(), DECREASE)).willReturn(List.of());

        assertThatThrownBy(() -> inboxDispatcher.dispatch(event))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_EXCEPTION);
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
}
