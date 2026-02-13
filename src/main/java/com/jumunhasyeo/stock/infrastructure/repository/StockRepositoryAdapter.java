package com.jumunhasyeo.stock.infrastructure.repository;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StockRepositoryAdapter implements StockRepository {
    private final JpaStockRepository jpaStockRepository;

    @Override
    public Optional<Stock> findByProductId(UUID productId) {
        return jpaStockRepository.findByProductId(productId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Stock> findByProductIdWithLock(UUID productId) {
        return jpaStockRepository.findStockByProductIdWithLock(productId);
    }

    @Override
    public boolean decreaseStock(UUID stockId, int amount) {
        boolean isSuccess = jpaStockRepository.decreaseStock(stockId, amount) == 1;
        if (!isSuccess) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        return isSuccess;
    }

    @Override
    public boolean increaseStock(UUID stockId, int amount) {
        boolean isSuccess = jpaStockRepository.increaseStock(stockId, amount) == 1;
        if (!isSuccess) {
            throw new BusinessException(ErrorCode.STOCK_MAX_EXCEEDED);
        }
        return isSuccess;
    }

    @Override
    public Optional<Stock> findById(UUID stockId) {
        return jpaStockRepository.findById(stockId);
    }

    @Override
    public Stock save(Stock stock) {
        return jpaStockRepository.save(stock);
    }

    @Override
    public List<Stock> findNextBatch(LocalDateTime lastCreatedAt, UUID lastId, Pageable pageable) {
        return jpaStockRepository.findNextBatch(
                lastCreatedAt,
                lastId,
                pageable
        );
    }
}
