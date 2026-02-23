package com.jumunhasyeo.stock.infrastructure.dynamic;

import com.jumunhasyeo.stock.application.StockVariationService;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockChangeRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 동적 StockVariationService 프록시
 * 
 * DEFAULT: 일반 구현체 (atomic update)
 * PESSIMISTIC_LOCK: 비관적 락 구현체
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "dynamic.enabled", havingValue = "true")
public class DynamicStockVariationServiceProxy implements StockVariationService {
    
    private final StockDynamicConfig config;
    private final StockVariationStrategyRegistry strategyRegistry;
    
    public DynamicStockVariationServiceProxy(
            StockDynamicConfig config,
            StockVariationStrategyRegistry strategyRegistry
    ) {
        this.config = config;
        this.strategyRegistry = strategyRegistry;
    }

    @Override
    public StockLockType type() {
        return StockLockType.DEFAULT;
    }

    private StockVariationService resolve() {
        String activeRaw = config.getStockLock();
        try {
            StockLockType activeType = StockLockType.valueOf(activeRaw.toUpperCase());
            return strategyRegistry.resolve(activeType);
        } catch (Exception e) {
            log.warn("[Dynamic] Unknown stock type '{}', fallback to DEFAULT", activeRaw);
            return strategyRegistry.resolve(StockLockType.DEFAULT);
        }
    }
    
    @Override
    public List<StockChangeRes> decrement(String idempotencyKey, List<DecreaseStockCommand> commands) {
        return resolve().decrement(idempotencyKey, commands);
    }

    @Override
    public List<StockChangeRes> increment(String idempotencyKey, List<IncreaseStockCommand> commands) {
        return resolve().increment(idempotencyKey, commands);
    }
}
