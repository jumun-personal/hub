package com.jumunhasyeo.stock.infrastructure.dynamic;

import com.jumunhasyeo.stock.application.StockVariationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * StockVariation 전략 조회 레지스트리
 */
@Slf4j
@Component
public class StockVariationStrategyRegistry {

    private final Map<StockLockType, StockVariationService> strategies;

    public StockVariationStrategyRegistry(
            @Qualifier("stockVariationStrategy") List<StockVariationService> services
    ) {
        this.strategies = new EnumMap<>(StockLockType.class);

        for (StockVariationService service : services) {
            StockLockType type = service.type();
            StockVariationService previous = strategies.put(type, service);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate StockVariationService strategy for type: " + type
                );
            }
        }

        log.info("[Dynamic] StockVariation strategies initialized: {}", strategies.keySet());
    }

    public StockVariationService resolve(StockLockType type) {
        StockVariationService strategy = strategies.get(type);
        if (strategy != null) {
            return strategy;
        }

        StockVariationService fallback = strategies.get(StockLockType.DEFAULT);
        if (fallback == null) {
            throw new IllegalStateException("DEFAULT StockVariationService strategy is not registered");
        }

        log.warn("[Dynamic] Strategy not found for type '{}', fallback to DEFAULT", type);
        return fallback;
    }
}
