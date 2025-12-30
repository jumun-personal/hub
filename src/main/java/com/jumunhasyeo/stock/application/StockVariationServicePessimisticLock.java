package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.common.dynamic.StockLockType;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Qualifier("stockVariationStrategy")
@ConditionalOnProperty(name = "dynamic.enabled", havingValue = "false", matchIfMissing = true)
public class StockVariationServicePessimisticLock implements StockVariationService {

    private final StockRepository stockRepository;

    @Override
    public StockLockType type() {
        return StockLockType.PESSIMISTIC_LOCK;
    }

    @Override
    @Transactional
    public StockRes decrement(DecreaseStockCommand command) {
        Stock stock = getStockByPessimisticLock(command.productId());
        stock.decrease(command.amount());
        return StockRes.from(stock);
    }

    @Override
    @Transactional
    public StockRes increment(IncreaseStockCommand command) {
        Stock stock = getStockByPessimisticLock(command.productId());
        stock.increase(command.amount());
        return StockRes.from(stock);
    }

    private Stock getStockByPessimisticLock(UUID productId) {
        return stockRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_EXCEPTION,"productId = "+productId));
    }
}
