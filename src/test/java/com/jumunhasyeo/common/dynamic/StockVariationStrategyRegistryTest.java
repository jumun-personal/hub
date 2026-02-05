package com.jumunhasyeo.stock.infrastructure.dynamic;

import com.jumunhasyeo.stock.application.StockVariationService;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockVariationStrategyRegistryTest {

    @Test
    @DisplayName("요청 타입 전략이 있으면 해당 전략을 반환한다.")
    void resolve_existing_type() {
        StockVariationService defaultStrategy = new StubStrategy(StockLockType.DEFAULT);
        StockVariationService pessimistic = new StubStrategy(StockLockType.PESSIMISTIC_LOCK);
        StockVariationStrategyRegistry registry = new StockVariationStrategyRegistry(List.of(defaultStrategy, pessimistic));

        StockVariationService resolved = registry.resolve(StockLockType.PESSIMISTIC_LOCK);

        assertThat(resolved).isEqualTo(pessimistic);
    }

    @Test
    @DisplayName("요청 타입 전략이 없으면 DEFAULT 전략으로 폴백한다.")
    void resolve_fallback_to_default() {
        StockVariationService defaultStrategy = new StubStrategy(StockLockType.DEFAULT);
        StockVariationStrategyRegistry registry = new StockVariationStrategyRegistry(List.of(defaultStrategy));

        StockVariationService resolved = registry.resolve(StockLockType.PESSIMISTIC_LOCK);

        assertThat(resolved).isEqualTo(defaultStrategy);
    }

    @Test
    @DisplayName("DEFAULT 전략도 없으면 예외를 던진다.")
    void resolve_without_default_throws() {
        StockVariationService pessimistic = new StubStrategy(StockLockType.PESSIMISTIC_LOCK);
        StockVariationStrategyRegistry registry = new StockVariationStrategyRegistry(List.of(pessimistic));

        assertThatThrownBy(() -> registry.resolve(StockLockType.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEFAULT StockVariationService strategy is not registered");
    }

    @Test
    @DisplayName("동일 타입 전략이 중복 등록되면 예외를 던진다.")
    void duplicate_type_throws() {
        StockVariationService defaultStrategy1 = new StubStrategy(StockLockType.DEFAULT);
        StockVariationService defaultStrategy2 = new StubStrategy(StockLockType.DEFAULT);

        assertThatThrownBy(() -> new StockVariationStrategyRegistry(List.of(defaultStrategy1, defaultStrategy2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate StockVariationService strategy for type: DEFAULT");
    }

    static class StubStrategy implements StockVariationService {

        private final StockLockType type;

        StubStrategy(StockLockType type) {
            this.type = type;
        }

        @Override
        public StockLockType type() {
            return type;
        }

        @Override
        public StockRes decrement(DecreaseStockCommand command) {
            return new StockRes(UUID.randomUUID(), UUID.randomUUID(), command.productId(), 0, null, null);
        }

        @Override
        public List<StockRes> decrement(List<DecreaseStockCommand> commands) {
            return commands.stream()
                    .map(this::decrement)
                    .toList();
        }

        @Override
        public StockRes increment(IncreaseStockCommand command) {
            return new StockRes(UUID.randomUUID(), UUID.randomUUID(), command.productId(), 0, null, null);
        }

        @Override
        public List<StockRes> increment(List<IncreaseStockCommand> commands) {
            return commands.stream()
                    .map(this::increment)
                    .toList();
        }
    }
}
