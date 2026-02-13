package com.jumunhasyeo.common.Idempotency.db.infrastructure.repository;

import com.jumunhasyeo.common.Idempotency.db.domain.IdempotencyKey;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JpaIdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
    @Query("SELECT i FROM IdempotencyKey i WHERE i.idempotencyKey = :key AND i.expiresAt > :now")
    Optional<IdempotencyKey> findByIdempotencyKeyAndNotExpired(
            @Param("key") String key,
            @Param("now") LocalDateTime now
    );

    @Query("SELECT i FROM IdempotencyKey i WHERE i.status = :status AND i.createdAt < :threshold")
    List<IdempotencyKey> findStaleProcessingKeys(@Param("status") IdempotentStatus status, @Param("threshold") LocalDateTime threshold);

    @Query("SELECT i FROM IdempotencyKey i WHERE i.expiresAt < :now")
    List<IdempotencyKey> findExpiredKeys(@Param("now") LocalDateTime now);
}
