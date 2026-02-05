package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.stock.infrastructure.dynamic.StockLockType;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Primary
@Qualifier("stockVariationStrategy")
@ConditionalOnProperty(name = "dynamic.enabled", havingValue = "false", matchIfMissing = true)
public class StockVariationServiceImpl implements StockVariationService {

    private final StockRepository stockRepository;
    private final EntityManager entityManager;

    @Override
    public StockLockType type() {
        return StockLockType.DEFAULT;
    }

    @Override
    public StockRes decrement(DecreaseStockCommand command) {
        Stock stock = getStock(command.productId());
        entityManager.detach(stock);
        stock.decrease(command.amount());
        StockRes res = StockRes.from(stock);
        stockRepository.decreaseStock(stock.getStockId(), command.amount());
        return res;
    }

    @Override
    @Transactional
    public List<StockRes> decrement(List<DecreaseStockCommand> commands) {
        return commands.stream()
                .map(this::decrement)
                .toList();
    }

    @Override
    public StockRes increment(IncreaseStockCommand command) {
        Stock stock = getStock(command.productId());
        entityManager.detach(stock);
        stock.increase(command.amount());
        StockRes res = StockRes.from(stock);
        stockRepository.increaseStock(stock.getStockId(), command.amount());
        return res;
    }

    @Override
    @Transactional
    public List<StockRes> increment(List<IncreaseStockCommand> commands) {
        return commands.stream()
                .map(this::increment)
                .toList();
    }

    private Stock getStock(UUID productId) {
        return stockRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_EXCEPTION, "productId = "+productId));
    }
}
