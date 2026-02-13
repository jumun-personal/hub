package com.jumunhasyeo.common.Idempotency.db.domain.repository;

import com.jumunhasyeo.common.Idempotency.db.domain.IdempotencyKey;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IdempotencyKeyRepository{
    Optional<IdempotencyKey> findByIdempotencyKeyAndNotExpired(String key, LocalDateTime now);
    IdempotencyKey save(IdempotencyKey idempotencyKey);
    List<IdempotencyKey> findStaleProcessingKeys(LocalDateTime threshold);
    List<IdempotencyKey> findExpiredKeys(LocalDateTime now);
    void deleteAll(List<IdempotencyKey> expiredKeys);
}
