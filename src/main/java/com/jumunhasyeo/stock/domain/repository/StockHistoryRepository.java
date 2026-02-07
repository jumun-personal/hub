package com.jumunhasyeo.stock.domain.repository;

import com.jumunhasyeo.stock.domain.entity.StockHistory;

import java.util.List;

public interface StockHistoryRepository {
    StockHistory save(StockHistory stockHistory);
    List<StockHistory> saveAll(List<StockHistory> stockHistories);
    List<StockHistory> findByIdempotencyKeyAndType(String idempotencyKey, StockHistory.StockHistoryType type);
}
