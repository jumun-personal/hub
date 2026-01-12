package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.stock.infrastructure.dynamic.StockLockType;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockVariationServicePessimisticLockTest {

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private StockVariationServicePessimisticLock service;

    @Test
    @DisplayName("전략 타입은 PESSIMISTIC_LOCK이다.")
    void strategy_type_pessimistic_lock() {
        assertThat(service.type()).isEqualTo(StockLockType.PESSIMISTIC_LOCK);
    }

    @Test
    @DisplayName("비관적 락으로 재고를 감소시킨다.")
    void decrement_success() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Stock stock = Stock.of(hubId, productId, 30);
        when(stockRepository.findByProductIdWithLock(productId)).thenReturn(Optional.of(stock));

        StockRes result = service.decrement(new DecreaseStockCommand(productId, 7));

        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.quantity()).isEqualTo(23);
    }

    @Test
    @DisplayName("비관적 락으로 재고를 증가시킨다.")
    void increment_success() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Stock stock = Stock.of(hubId, productId, 30);
        when(stockRepository.findByProductIdWithLock(productId)).thenReturn(Optional.of(stock));

        StockRes result = service.increment(new IncreaseStockCommand(productId, 7));

        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.quantity()).isEqualTo(37);
    }

    @Test
    @DisplayName("비관적 락 조회에 실패하면 NOT_FOUND_EXCEPTION 예외를 던진다.")
    void get_by_lock_not_found() {
        UUID productId = UUID.randomUUID();
        when(stockRepository.findByProductIdWithLock(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decrement(new DecreaseStockCommand(productId, 1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_EXCEPTION);
    }
}
