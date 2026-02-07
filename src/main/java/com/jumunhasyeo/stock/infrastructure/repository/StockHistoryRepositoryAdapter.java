package com.jumunhasyeo.stock.infrastructure.repository;

import com.jumunhasyeo.stock.domain.entity.StockHistory;
import com.jumunhasyeo.stock.domain.repository.StockHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class StockHistoryRepositoryAdapter implements StockHistoryRepository {
    
    private final JpaStockHistoryRepository jpaStockHistoryRepository;

    @Override
    public StockHistory save(StockHistory stockHistory) {
        return jpaStockHistoryRepository.save(stockHistory);
    }

    @Override
    public List<StockHistory> saveAll(List<StockHistory> stockHistories) {
        return jpaStockHistoryRepository.saveAll(stockHistories);
    }

    @Override
    public List<StockHistory> findByIdempotencyKeyAndType(String idempotencyKey, StockHistory.StockHistoryType type) {
        return jpaStockHistoryRepository.findByIdempotencyKeyAndType(idempotencyKey, type);
    }
}
