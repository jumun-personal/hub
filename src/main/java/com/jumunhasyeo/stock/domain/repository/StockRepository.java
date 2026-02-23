package com.jumunhasyeo.stock.domain.repository;

import com.jumunhasyeo.stock.domain.entity.Stock;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {

    Optional<Stock> findByHubIdAndProductId(UUID hubId, UUID productId);

    Optional<Stock> findByHubIdAndProductIdWithLock(UUID hubId, UUID productId);

    boolean decreaseStock(UUID hubId, UUID productId, int quantity);

    boolean increaseStock(UUID hubId, UUID productId, int amount);

    Optional<Stock> findById(UUID stockId);

    Stock save(Stock stock);

    List<Stock> findNextBatch(LocalDateTime lastCreatedAt, UUID lastId, Pageable pageable);
}
