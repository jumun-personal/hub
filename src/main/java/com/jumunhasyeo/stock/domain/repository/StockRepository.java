package com.jumunhasyeo.stock.domain.repository;

import com.jumunhasyeo.stock.domain.entity.Stock;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {

    Optional<Stock> findByProductId(UUID productId);

    Optional<Stock> findByProductIdWithLock(UUID productId);

    boolean decreaseStock(UUID stockId, int quantity);

    boolean increaseStock(UUID stockId, int amount);

    Optional<Stock> findById(UUID stockId);

    Stock save(Stock stock);

    List<Stock> findNextBatch(LocalDateTime lastCreatedAt, UUID lastId, Pageable pageable);
}
