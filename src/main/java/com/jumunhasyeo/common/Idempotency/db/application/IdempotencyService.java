package com.jumunhasyeo.common.Idempotency.db.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotencyKey;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus;

public interface IdempotencyService {
    void saveStatus(String statusKey, IdempotentStatus status, long ttlSeconds);
    Boolean setIfAbsent(String statusKey, IdempotentStatus status, long ttlSeconds, Object payload) throws JsonProcessingException;
    IdempotentStatus getCurrentStatus(String statusKey);
    void saveError(String statusKey, String errorMsg, long ttlSeconds);
    IdempotencyKey get(String key);
}
