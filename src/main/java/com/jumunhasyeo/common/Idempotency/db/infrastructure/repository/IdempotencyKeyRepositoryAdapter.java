package com.jumunhasyeo.common.Idempotency.db.infrastructure.repository;

import com.jumunhasyeo.common.Idempotency.db.domain.IdempotencyKey;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus;
import com.jumunhasyeo.common.Idempotency.db.domain.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IdempotencyKeyRepositoryAdapter implements IdempotencyKeyRepository {
    private final JpaIdempotencyKeyRepository repository;

    @Override
    public Optional<IdempotencyKey> findByIdempotencyKeyAndNotExpired(String key, LocalDateTime now) {
        return repository.findByIdempotencyKeyAndNotExpired(key, now);
    }

    @Override
    public IdempotencyKey save(IdempotencyKey idempotencyKey) {
        return repository.save(idempotencyKey);
    }

    @Override
    public List<IdempotencyKey> findStaleProcessingKeys(LocalDateTime threshold) {
        return repository.findStaleProcessingKeys(IdempotentStatus.PROCESSING, threshold);
    }

    @Override
    public List<IdempotencyKey> findExpiredKeys(LocalDateTime now) {
        return repository.findExpiredKeys(now);
    }

    @Override
    public void deleteAll(List<IdempotencyKey> expiredKeys) {
        repository.deleteAll(expiredKeys);
    }
}
