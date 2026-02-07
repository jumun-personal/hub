package com.jumunhasyeo.common.Idempotency;

import com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus;
import com.jumunhasyeo.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

import static com.jumunhasyeo.common.exception.ErrorCode.PROCESSING_CONFLICT_EXCEPTION;
import static com.jumunhasyeo.common.exception.ErrorCode.SUCCESS_CONFLICT_EXCEPTION;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DbIdempotentAspect {
    private final StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(dbIdempotent)")
    public Object handleIdempotency(
            ProceedingJoinPoint joinPoint,
            DbIdempotent dbIdempotent
    ) throws Throwable {

        Object[] args = joinPoint.getArgs();
        // 첫 번째 파라미터 = 멱등키
        String rawKey = (String) args[0];
        String statusKey = composeStatusKey(dbIdempotent, rawKey);
        long ttlSeconds = getTtlSeconds(dbIdempotent);
        log.info("DbIdempotent request - key: {}, ttl: {} days", statusKey, dbIdempotent.ttlDays());

        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(statusKey, IdempotentStatus.PROCESSING.name(), Duration.ofSeconds(ttlSeconds));
        if (!Boolean.TRUE.equals(acquired)) {
            throwConflict(statusKey);
        }

        try {
            return proceed(joinPoint, statusKey, ttlSeconds);
        } catch (Exception e) { // 5. 실패 → FAILED + 에러 저장 (재시도 가능)
            fail(e, statusKey);
            throw e;
        }
    }

    private Object proceed(ProceedingJoinPoint joinPoint, String statusKey, long ttlSeconds) throws Throwable {
        log.info("Executing business logic for key: {}", statusKey);
        Object result = joinPoint.proceed();
        stringRedisTemplate.opsForValue()
                .set(statusKey, IdempotentStatus.SUCCESS.name(), Duration.ofSeconds(ttlSeconds));
        log.info("Successfully completed and cached result for key: {}", statusKey);
        return result;
    }

    private void fail(Exception e, String statusKey) {
        log.error("Business logic failed for key: {}", statusKey, e);
        stringRedisTemplate.delete(statusKey);
    }

    private void throwConflict(String statusKey) {
        String status = stringRedisTemplate.opsForValue().get(statusKey);
        if (IdempotentStatus.SUCCESS.name().equals(status)) {
            throw new BusinessException(SUCCESS_CONFLICT_EXCEPTION);
        }
        throw new BusinessException(PROCESSING_CONFLICT_EXCEPTION);
    }

    private long getTtlSeconds(DbIdempotent dbIdempotent) {
        return dbIdempotent.ttlDays() * 24 * 3600L;
    }

    private String composeStatusKey(DbIdempotent dbIdempotent, String rawKey) {
        String keyPrefix = dbIdempotent.keyPrefix();
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return rawKey;
        }
        return keyPrefix + rawKey;
    }
}
