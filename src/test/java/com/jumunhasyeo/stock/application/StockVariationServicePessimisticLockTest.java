package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockChangeRes;
import com.jumunhasyeo.stock.domain.entity.StockHistory;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.stock.domain.repository.StockHistoryRepository;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockVariationServicePessimisticLockTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockHistoryRepository stockHistoryRepository;

    @InjectMocks
    private StockVariationServicePessimisticLock service;

    @Test
    @DisplayName("비관적 락으로 재고를 감소시킨다.")
    void decrement_success() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(hubId, productId, 30);
        when(stockRepository.findByHubIdAndProductIdWithLock(hubId, productId)).thenReturn(Optional.of(stock));

        StockChangeRes result = service.decrement("idem-decrease", List.of(new DecreaseStockCommand(hubId, productId, 7))).get(0);

        assertThat(result.hubId()).isEqualTo(hubId);
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.type()).isEqualTo(StockHistory.StockHistoryType.DECREASE);
        assertThat(result.quantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("비관적 락으로 재고를 증가시킨다.")
    void increment_success() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(hubId, productId, 30);
        when(stockRepository.findByHubIdAndProductIdWithLock(hubId, productId)).thenReturn(Optional.of(stock));

        StockChangeRes result = service.increment("idem-increase", List.of(new IncreaseStockCommand(hubId, productId, 7))).get(0);

        assertThat(result.hubId()).isEqualTo(hubId);
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.type()).isEqualTo(StockHistory.StockHistoryType.INCREASE);
        assertThat(result.quantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("비관적 락 조회에 실패하면 NOT_FOUND_EXCEPTION 예외를 던진다.")
    void get_by_lock_not_found() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(stockRepository.findByHubIdAndProductIdWithLock(hubId, productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decrement("idem-not-found", List.of(new DecreaseStockCommand(hubId, productId, 1))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_EXCEPTION);
    }

    private Stock createStock(UUID hubId, UUID productId, int quantity) {
        return Stock.builder()
                .stockId(UUID.randomUUID())
                .hubId(hubId)
                .productId(productId)
                .quantity(quantity)
                .build();
    }
}
