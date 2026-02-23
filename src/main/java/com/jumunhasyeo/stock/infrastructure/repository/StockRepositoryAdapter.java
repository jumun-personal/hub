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
    public Optional<Stock> findByHubIdAndProductId(UUID hubId, UUID productId) {
        return jpaStockRepository.findByHubIdAndProductId(hubId, productId);
    }

    @Override
    public Optional<Stock> findByHubIdAndProductIdWithLock(UUID hubId, UUID productId) {
        return jpaStockRepository.findStockByHubIdAndProductIdWithLock(hubId, productId);
    }

    @Override
    public boolean decreaseStock(UUID hubId, UUID productId, int amount) {
        boolean isSuccess = jpaStockRepository.decreaseStock(hubId, productId, amount) == 1;
        if (!isSuccess) {
            if (jpaStockRepository.findByHubIdAndProductId(hubId, productId).isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND_EXCEPTION, "hubId = " + hubId + ", productId = " + productId);
            }
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        return isSuccess;
    }

    @Override
    public boolean increaseStock(UUID hubId, UUID productId, int amount) {
        boolean isSuccess = jpaStockRepository.increaseStock(hubId, productId, amount) == 1;
        if (!isSuccess) {
            if (jpaStockRepository.findByHubIdAndProductId(hubId, productId).isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND_EXCEPTION, "hubId = " + hubId + ", productId = " + productId);
            }
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
