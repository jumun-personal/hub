package com.jumunhasyeo.common.Idempotency.db.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotencyKey;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotentType;
import com.jumunhasyeo.common.Idempotency.db.domain.repository.IdempotencyKeyRepository;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersistentIdempotencyService implements IdempotencyService {
    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public IdempotentStatus getCurrentStatus(String key) {
        return repository.findByIdempotencyKeyAndNotExpired(key, LocalDateTime.now())
                .map(IdempotencyKey::getStatus)
                .orElse(IdempotentStatus.NONE);
    }

    @Transactional
    public Boolean setIfAbsent(String key, IdempotentStatus status, long ttlSeconds, Object payload){
        try {
            IdempotencyKey entity = IdempotencyKey.create(
                    key,
                    status,
                    ttlSeconds,
                    IdempotentType.STOCK,
                    objectMapper.writeValueAsString(payload)
            );
            repository.save(entity);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("Key already exists (race condition): {}", key);
            return false;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "JsonProcessingException : " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while setting key: {}", key, e);
            throw e;
        }
    }

    @Transactional
    public void saveStatus(String key, IdempotentStatus status, long ttlSeconds) {
        repository.findByIdempotencyKeyAndNotExpired(key, LocalDateTime.now())
                .ifPresent(entity -> {
                    entity.updateStatus(status);
                    repository.save(entity);
                });
    }

    @Transactional
    public void saveError(String key, String errorMessage, long ttlSeconds) {
        repository.findByIdempotencyKeyAndNotExpired(key, LocalDateTime.now())
                .ifPresent(entity -> {
                    entity.applyError(errorMessage);
                    repository.save(entity);
                });
    }

    @Override
    public IdempotencyKey get(String key) {
        return repository.findByIdempotencyKeyAndNotExpired(key, LocalDateTime.now())
                .orElse(null);
    }
}
