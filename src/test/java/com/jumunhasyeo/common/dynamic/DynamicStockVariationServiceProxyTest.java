package com.jumunhasyeo.common.dynamic;

import com.jumunhasyeo.stock.application.StockVariationService;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicStockVariationServiceProxyTest {

    @Mock
    private DynamicConfig config;

    @Mock
    private StockVariationStrategyRegistry strategyRegistry;

    @Mock
    private StockVariationService defaultStrategy;

    @Mock
    private StockVariationService pessimisticStrategy;

    @InjectMocks
    private DynamicStockVariationServiceProxy proxy;

    @Test
    @DisplayName("DEFAULT 설정이면 기본 전략을 사용한다.")
    void decrement_default_strategy() {
        DecreaseStockCommand command = new DecreaseStockCommand(UUID.randomUUID(), 3);
        StockRes expected = new StockRes(UUID.randomUUID(), UUID.randomUUID(), command.productId(), 7, null, null);

        when(config.getStockLock()).thenReturn("DEFAULT");
        when(strategyRegistry.resolve(StockLockType.DEFAULT)).thenReturn(defaultStrategy);
        when(defaultStrategy.decrement(command)).thenReturn(expected);

        StockRes result = proxy.decrement(command);

        assertThat(result).isEqualTo(expected);
        verify(strategyRegistry).resolve(StockLockType.DEFAULT);
        verify(defaultStrategy).decrement(command);
    }

    @Test
    @DisplayName("PESSIMISTIC_LOCK 설정이면 비관적 락 전략을 사용한다.")
    void increment_pessimistic_strategy() {
        IncreaseStockCommand command = new IncreaseStockCommand(UUID.randomUUID(), 4);
        StockRes expected = new StockRes(UUID.randomUUID(), UUID.randomUUID(), command.productId(), 11, null, null);

        when(config.getStockLock()).thenReturn("PESSIMISTIC_LOCK");
        when(strategyRegistry.resolve(StockLockType.PESSIMISTIC_LOCK)).thenReturn(pessimisticStrategy);
        when(pessimisticStrategy.increment(command)).thenReturn(expected);

        StockRes result = proxy.increment(command);

        assertThat(result).isEqualTo(expected);
        verify(strategyRegistry).resolve(StockLockType.PESSIMISTIC_LOCK);
        verify(pessimisticStrategy).increment(command);
    }

    @Test
    @DisplayName("알 수 없는 설정값이면 DEFAULT 전략으로 폴백한다.")
    void unknown_type_fallback_to_default() {
        DecreaseStockCommand command = new DecreaseStockCommand(UUID.randomUUID(), 1);
        StockRes expected = new StockRes(UUID.randomUUID(), UUID.randomUUID(), command.productId(), 99, null, null);

        when(config.getStockLock()).thenReturn("UNKNOWN_TYPE");
        when(strategyRegistry.resolve(StockLockType.DEFAULT)).thenReturn(defaultStrategy);
        when(defaultStrategy.decrement(command)).thenReturn(expected);

        StockRes result = proxy.decrement(command);

        assertThat(result).isEqualTo(expected);
        verify(strategyRegistry).resolve(StockLockType.DEFAULT);
        verify(defaultStrategy).decrement(command);
    }
}
