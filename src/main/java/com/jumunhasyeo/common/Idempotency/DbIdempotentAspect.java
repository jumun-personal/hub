package com.jumunhasyeo.common.Idempotency;

import com.jumunhasyeo.common.Idempotency.db.application.IdempotentService;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus;
import com.jumunhasyeo.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import static com.jumunhasyeo.common.exception.ErrorCode.PROCESSING_CONFLICT_EXCEPTION;
import static com.jumunhasyeo.common.exception.ErrorCode.SUCCESS_CONFLICT_EXCEPTION;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DbIdempotentAspect {
    private final IdempotentService idempotentService;

    @Around("@annotation(dbIdempotent)")
    public Object handleIdempotency(
            ProceedingJoinPoint joinPoint,
            DbIdempotent dbIdempotent
    ) throws Throwable {

        Object[] args = joinPoint.getArgs();
        // 첫 번째 파라미터 = 멱등키
        String rawKey = (String) args[0];
        String statusKey = composeStatusKey(dbIdempotent, rawKey);
        // 두 번째 파라미터 = 페이로드
        Object payload = "";
        if(args.length >= 2){
            payload = args[1];
        }
        long ttlSeconds = getTtlSeconds(dbIdempotent);
        log.info("DbIdempotent request - key: {}, ttl: {} days", statusKey, dbIdempotent.ttlDays());

        // 1. 상태 확인
        IdempotentStatus idempotentStatus = idempotentService.getCurrentStatus(statusKey);

        // 2-1. COMPLETED:
        if (isSuccess(idempotentStatus)) {
            throw new BusinessException(SUCCESS_CONFLICT_EXCEPTION);
        }

        // 2-2. PROCESSING: SET NX (없을 때만 PROCESSING 설정)
        Boolean acquired = idempotentService.setIfAbsent(statusKey, IdempotentStatus.PROCESSING, ttlSeconds, payload);
        if (isProcessing(acquired)){
            throw new BusinessException(PROCESSING_CONFLICT_EXCEPTION);
        }

        // 3. FAILED 또는 없음 → 처리 시작
        try {
            return proceed(joinPoint, statusKey, ttlSeconds);
        } catch (Exception e) { // 5. 실패 → FAILED + 에러 저장 (재시도 가능)
            fail(e, statusKey, ttlSeconds);
            throw e;
        }
    }

    private boolean isSuccess(IdempotentStatus idempotentStatus) {
        return idempotentStatus.isSuccess();
    }

    private boolean isProcessing(Boolean acquired) {
        // 이미 processing이 존재 한다면 or set을 실패 했다면
        return Boolean.FALSE.equals(acquired);
    }

    private Object proceed(ProceedingJoinPoint joinPoint, String statusKey, long ttlSeconds) throws Throwable {
        log.info("Executing business logic for key: {}", statusKey);
        Object result = joinPoint.proceed();
        idempotentService.saveStatus(statusKey, IdempotentStatus.SUCCESS, ttlSeconds);
        log.info("Successfully completed and cached result for key: {}", statusKey);
        return result;
    }

    private void fail(Exception e, String statusKey, long ttlSeconds) {
        log.error("Business logic failed for key: {}", statusKey, e);
        idempotentService.saveStatus(statusKey, IdempotentStatus.FAIL, ttlSeconds);
        idempotentService.saveError(statusKey, getErrorMsg(e), ttlSeconds);
    }

    private String getErrorMsg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Unknown error";
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
