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

import static com.jumunhasyeo.common.exception.ErrorCode.INVALID_INPUT;
import static com.jumunhasyeo.common.exception.ErrorCode.PROCESSING_CONFLICT_EXCEPTION;
import static com.jumunhasyeo.common.exception.ErrorCode.SUCCESS_CONFLICT_EXCEPTION;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {
    private final StringRedisTemplate stringRedisTemplate;

    @Around(value = "@annotation(idempotent)", argNames = "joinPoint,idempotent")
    public Object handleIdempotency(
            ProceedingJoinPoint joinPoint,
            Idempotent idempotent
    ) throws Throwable {

        String rawKey = extractIdempotencyKey(joinPoint.getArgs());
        String statusKey = composeStatusKey(idempotent, rawKey);
        Duration processingTtl = Duration.ofSeconds(idempotent.processingTtlSeconds());
        Duration successTtl = Duration.ofSeconds(idempotent.successTtlSeconds());
        log.info(
                "Idempotent request - key: {}, processingTtlSeconds: {}, successTtlSeconds: {}",
                statusKey,
                idempotent.processingTtlSeconds(),
                idempotent.successTtlSeconds()
        );

        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(statusKey, IdempotentStatus.PROCESSING.name(), processingTtl);
        if (!Boolean.TRUE.equals(acquired)) {
            throwConflict(statusKey);
        }

        try {
            Object result = joinPoint.proceed();
            markSuccess(statusKey, successTtl);
            return result;
        } catch (Exception e) { // 5. 실패 → FAILED + 에러 저장 (재시도 가능)
            fail(e, statusKey);
            throw e;
        }
    }

    private String extractIdempotencyKey(Object[] args) {
        if (args == null || args.length == 0 || !(args[0] instanceof String rawKey) || rawKey.isBlank()) {
            throw new BusinessException(INVALID_INPUT, "첫 번째 파라미터에는 비어 있지 않은 멱등키가 필요합니다.");
        }
        return rawKey;
    }

    private void markSuccess(String statusKey, Duration successTtl) {
        try {
            stringRedisTemplate.opsForValue()
                    .set(statusKey, IdempotentStatus.SUCCESS.name(), successTtl);
            log.info("Successfully completed and cached result for key: {}", statusKey);
        } catch (RuntimeException e) {
            log.error("Business logic succeeded but failed to mark idempotency key as SUCCESS. key={}", statusKey, e);
        }
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

    private String composeStatusKey(Idempotent idempotent, String rawKey) {
        String keyPrefix = idempotent.keyPrefix();
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return rawKey;
        }
        return keyPrefix + rawKey;
    }
}
