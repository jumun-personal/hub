package com.jumunhasyeo.stock.infrastructure.dynamic;

import com.jumunhasyeo.stock.application.StockVariationService;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockChangeRes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicStockVariationServiceProxyTest {

    @Mock
    private StockDynamicConfig config;

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
        DecreaseStockCommand command = new DecreaseStockCommand(UUID.randomUUID(), UUID.randomUUID(), 3);
        StockChangeRes expected = StockChangeRes.decrease(UUID.randomUUID(), command.productId(), command.amount());

        when(config.getStockLock()).thenReturn("DEFAULT");
        when(strategyRegistry.resolve(StockLockType.DEFAULT)).thenReturn(defaultStrategy);
        when(defaultStrategy.decrement("idem-decrease", List.of(command))).thenReturn(List.of(expected));

        StockChangeRes result = proxy.decrement("idem-decrease", List.of(command)).get(0);

        assertThat(result).isEqualTo(expected);
        verify(strategyRegistry).resolve(StockLockType.DEFAULT);
        verify(defaultStrategy).decrement("idem-decrease", List.of(command));
    }

    @Test
    @DisplayName("PESSIMISTIC_LOCK 설정이면 비관적 락 전략을 사용한다.")
    void increment_pessimistic_strategy() {
        IncreaseStockCommand command = new IncreaseStockCommand(UUID.randomUUID(), UUID.randomUUID(), 4);
        StockChangeRes expected = StockChangeRes.increase(UUID.randomUUID(), command.productId(), command.amount());

        when(config.getStockLock()).thenReturn("PESSIMISTIC_LOCK");
        when(strategyRegistry.resolve(StockLockType.PESSIMISTIC_LOCK)).thenReturn(pessimisticStrategy);
        when(pessimisticStrategy.increment("idem-increase", List.of(command))).thenReturn(List.of(expected));

        StockChangeRes result = proxy.increment("idem-increase", List.of(command)).get(0);

        assertThat(result).isEqualTo(expected);
        verify(strategyRegistry).resolve(StockLockType.PESSIMISTIC_LOCK);
        verify(pessimisticStrategy).increment("idem-increase", List.of(command));
    }

    @Test
    @DisplayName("알 수 없는 설정값이면 DEFAULT 전략으로 폴백한다.")
    void unknown_type_fallback_to_default() {
        DecreaseStockCommand command = new DecreaseStockCommand(UUID.randomUUID(), UUID.randomUUID(), 1);
        StockChangeRes expected = StockChangeRes.decrease(UUID.randomUUID(), command.productId(), command.amount());

        when(config.getStockLock()).thenReturn("UNKNOWN_TYPE");
        when(strategyRegistry.resolve(StockLockType.DEFAULT)).thenReturn(defaultStrategy);
        when(defaultStrategy.decrement("idem-fallback", List.of(command))).thenReturn(List.of(expected));

        StockChangeRes result = proxy.decrement("idem-fallback", List.of(command)).get(0);

        assertThat(result).isEqualTo(expected);
        verify(strategyRegistry).resolve(StockLockType.DEFAULT);
        verify(defaultStrategy).decrement("idem-fallback", List.of(command));
    }
}
