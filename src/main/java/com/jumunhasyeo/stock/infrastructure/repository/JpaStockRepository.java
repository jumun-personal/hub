package com.jumunhasyeo.stock.infrastructure.repository;

import com.jumunhasyeo.stock.domain.entity.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaStockRepository extends JpaRepository<Stock, UUID> {
    @Query("SELECT s FROM Stock s WHERE s.stockId = :id AND s.isDeleted = false")
    Optional<Stock> findById(@Param("id") UUID id);

    @Query("SELECT s FROM Stock s " +
            "WHERE s.hubId = :hubId " +
            "AND s.productId = :productId " +
            "AND s.isDeleted = false")
    Optional<Stock> findByHubIdAndProductId(@Param("hubId") UUID hubId, @Param("productId") UUID productId);

    @Modifying
    @Query("""
            UPDATE Stock s
            SET s.quantity = s.quantity - :amount
            WHERE s.hubId = :hubId
              AND s.productId = :productId
              AND s.isDeleted = false
              AND s.quantity >= :amount
            """)
    int decreaseStock(@Param("hubId") UUID hubId, @Param("productId") UUID productId, @Param("amount") int amount);

    @Modifying
    @Query("""
            UPDATE Stock s
            SET s.quantity = s.quantity + :amount
            WHERE s.hubId = :hubId
              AND s.productId = :productId
              AND s.isDeleted = false
              AND s.quantity <= 2147483647 - :amount
            """)
    int increaseStock(@Param("hubId") UUID hubId, @Param("productId") UUID productId, @Param("amount") int amount);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s " +
            "WHERE s.hubId = :hubId " +
            "AND s.productId = :productId " +
            "AND s.isDeleted = false")
    Optional<Stock> findStockByHubIdAndProductIdWithLock(@Param("hubId") UUID hubId, @Param("productId") UUID productId);

    @Query("""
        SELECT s FROM Stock s 
        WHERE (s.createdAt > :lastCreatedAt)
           OR (s.createdAt = :lastCreatedAt AND s.stockId > :lastStockId)
        ORDER BY s.createdAt ASC, s.stockId ASC
        """)
    List<Stock> findNextBatch(
            @Param("lastCreatedAt") LocalDateTime lastCreatedAt,
            @Param("lastStockId") UUID lastStockId,
            Pageable pageable
    );
}
