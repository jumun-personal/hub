package com.jumunhasyeo.stock.infrastructure.repository;

import com.jumunhasyeo.stock.domain.entity.StockHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaStockHistoryRepository extends JpaRepository<StockHistory, UUID> {
    List<StockHistory> findByIdempotencyKeyAndType(String idempotencyKey, StockHistory.StockHistoryType type);
}
