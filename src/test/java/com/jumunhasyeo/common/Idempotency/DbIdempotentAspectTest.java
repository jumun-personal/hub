package com.jumunhasyeo.common.Idempotency;

import com.jumunhasyeo.common.Idempotency.db.application.IdempotentService;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus;
import com.jumunhasyeo.common.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.jumunhasyeo.common.exception.ErrorCode.PROCESSING_CONFLICT_EXCEPTION;
import static com.jumunhasyeo.common.exception.ErrorCode.SUCCESS_CONFLICT_EXCEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DbIdempotentAspectTest {
    @Mock
    private IdempotentService idempotentService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private DbIdempotent dbIdempotent;

    @InjectMocks
    private DbIdempotentAspect dbIdempotentAspect;

    private static final String TEST_KEY = "test-idempotency-key";
    private static final String PREFIXED_KEY = "STOCK_STORE:" + TEST_KEY;
    private static final int TTL_DAYS = 7;
    private static final long TTL_SECONDS = TTL_DAYS * 24 * 3600L;
    private static final Object EXPECTED_RESULT = "test-result";
    private static final String payload = "";

    @BeforeEach
    void setUp() {
        given(dbIdempotent.ttlDays()).willReturn(TTL_DAYS);
        given(dbIdempotent.keyPrefix()).willReturn("");
        given(joinPoint.getArgs()).willReturn(new Object[]{TEST_KEY});
    }

    @Test
    @DisplayName("SUCCESS 상태일 때 SUCCESS_CONFLICT_EXCEPTION 발생")
    void handleIdempotency_WhenStatusIsSuccess_ThrowsSuccessConflictException() throws Throwable {
        // given
        IdempotentStatus successStatus = IdempotentStatus.SUCCESS;
        given(idempotentService.getCurrentStatus(TEST_KEY)).willReturn(successStatus);

        // when & then
        assertThatThrownBy(() -> dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", SUCCESS_CONFLICT_EXCEPTION);

        // verify
        then(idempotentService).should().getCurrentStatus(TEST_KEY);
        then(idempotentService).should(never()).setIfAbsent(anyString(), any(), anyLong(),any());
        then(joinPoint).should(never()).proceed();
    }

    @Test
    @DisplayName("PROCESSING 상태일 때 PROCESSING_CONFLICT_EXCEPTION 발생")
    void handleIdempotency_WhenStatusIsProcessing_ThrowsProcessingConflictException() throws Throwable {
        // given
        IdempotentStatus processingStatus = IdempotentStatus.PROCESSING;
        given(idempotentService.getCurrentStatus(TEST_KEY)).willReturn(processingStatus);
        given(idempotentService.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING, TTL_SECONDS, payload))
                .willReturn(false); // SET NX 실패 (이미 존재)

        // when & then
        assertThatThrownBy(() -> dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PROCESSING_CONFLICT_EXCEPTION);

        // verify
        then(idempotentService).should().getCurrentStatus(TEST_KEY);
        then(idempotentService).should().setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING, TTL_SECONDS, payload);
        then(joinPoint).should(never()).proceed();
    }

    @Test
    @DisplayName("FAILED 상태일 때 재처리 후 성공")
    void handleIdempotency_WhenStatusIsFailed_ReprocessAndSuccess() throws Throwable {
        // given
        IdempotentStatus failedStatus = IdempotentStatus.FAIL;
        given(idempotentService.getCurrentStatus(TEST_KEY)).willReturn(failedStatus);
        given(idempotentService.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING, TTL_SECONDS, payload))
                .willReturn(true); // SET NX 성공
        given(joinPoint.proceed()).willReturn(EXPECTED_RESULT);

        // when
        Object result = dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent);

        // then
        assertThat(result).isEqualTo(EXPECTED_RESULT);

        // verify
        then(idempotentService).should().getCurrentStatus(TEST_KEY);
        then(idempotentService).should().setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING, TTL_SECONDS, payload);
        then(joinPoint).should().proceed();
        then(idempotentService).should().saveStatus(TEST_KEY, IdempotentStatus.SUCCESS, TTL_SECONDS);
        then(idempotentService).should(never()).saveError(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("상태 없을 때 정상 처리 후 성공")
    void handleIdempotency_WhenNoStatus_ProcessAndSuccess() throws Throwable {
        // given
        IdempotentStatus noStatus = IdempotentStatus.NONE; // 또는 null 처리 방식에 따라
        given(idempotentService.getCurrentStatus(TEST_KEY)).willReturn(noStatus);
        given(idempotentService.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING, TTL_SECONDS, payload))
                .willReturn(true); // SET NX 성공
        given(joinPoint.proceed()).willReturn(EXPECTED_RESULT);

        // when
        Object result = dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent);

        // then
        assertThat(result).isEqualTo(EXPECTED_RESULT);

        // verify
        then(idempotentService).should().getCurrentStatus(TEST_KEY);
        then(idempotentService).should().setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING, TTL_SECONDS, payload);
        then(joinPoint).should().proceed();
        then(idempotentService).should().saveStatus(TEST_KEY, IdempotentStatus.SUCCESS, TTL_SECONDS);
        then(idempotentService).should(never()).saveError(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("비즈니스 로직 실행 중 예외 발생 시 FAILED 상태 저장")
    void handleIdempotency_WhenBusinessLogicFails_SaveFailedStatus() throws Throwable {
        // given
        IdempotentStatus noStatus = IdempotentStatus.NONE;
        RuntimeException expectedException = new RuntimeException("Business logic error");

        given(idempotentService.getCurrentStatus(TEST_KEY)).willReturn(noStatus);
        given(idempotentService.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING, TTL_SECONDS, payload))
                .willReturn(true);
        given(joinPoint.proceed()).willThrow(expectedException);

        // when & then
        assertThatThrownBy(() -> dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Business logic error");

        // verify
        then(idempotentService).should().getCurrentStatus(TEST_KEY);
        then(idempotentService).should().setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING, TTL_SECONDS, payload);
        then(joinPoint).should().proceed();
        then(idempotentService).should().saveStatus(TEST_KEY, IdempotentStatus.FAIL, TTL_SECONDS);
        then(idempotentService).should().saveError(TEST_KEY, "Business logic error", TTL_SECONDS);
        then(idempotentService).should(never()).saveStatus(TEST_KEY, IdempotentStatus.SUCCESS, TTL_SECONDS);
    }

    @Test
    @DisplayName("예외 메시지가 null일 때 Unknown error 저장")
    void handleIdempotency_WhenExceptionMessageIsNull_SavesUnknownError() throws Throwable {
        // given
        IdempotentStatus noStatus = IdempotentStatus.NONE;
        RuntimeException exceptionWithNullMessage = new RuntimeException();

        given(idempotentService.getCurrentStatus(TEST_KEY)).willReturn(noStatus);
        given(idempotentService.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING, TTL_SECONDS, payload))
                .willReturn(true);
        given(joinPoint.proceed()).willThrow(exceptionWithNullMessage);

        // when & then
        assertThatThrownBy(() -> dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent))
                .isInstanceOf(RuntimeException.class);

        // verify
        then(idempotentService).should().saveStatus(TEST_KEY, IdempotentStatus.FAIL, TTL_SECONDS);
        then(idempotentService).should().saveError(TEST_KEY, "Unknown error", TTL_SECONDS);
    }

    @Test
    @DisplayName("TTL 계산이 정확한지 확인")
    void handleIdempotency_CalculatesTtlCorrectly() throws Throwable {
        // given
        int customTtlDays = 3;
        long expectedTtlSeconds = customTtlDays * 24 * 3600L;

        given(dbIdempotent.ttlDays()).willReturn(customTtlDays);
        given(idempotentService.getCurrentStatus(TEST_KEY)).willReturn(IdempotentStatus.NONE);
        given(idempotentService.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING, expectedTtlSeconds, payload))
                .willReturn(true);
        given(joinPoint.proceed()).willReturn(EXPECTED_RESULT);

        // when
        dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent);

        // then
        then(idempotentService).should().setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING, expectedTtlSeconds, payload);
        then(idempotentService).should().saveStatus(TEST_KEY, IdempotentStatus.SUCCESS, expectedTtlSeconds);
    }

    @Test
    @DisplayName("keyPrefix가 있으면 접두어가 붙은 키로 상태를 저장한다.")
    void handleIdempotency_WithKeyPrefix_UsesPrefixedStatusKey() throws Throwable {
        // given
        given(dbIdempotent.keyPrefix()).willReturn("STOCK_STORE:");
        given(joinPoint.getArgs()).willReturn(new Object[]{TEST_KEY, "payload-body"});
        given(idempotentService.getCurrentStatus(PREFIXED_KEY)).willReturn(IdempotentStatus.NONE);
        given(idempotentService.setIfAbsent(PREFIXED_KEY, IdempotentStatus.PROCESSING, TTL_SECONDS, "payload-body"))
                .willReturn(true);
        given(joinPoint.proceed()).willReturn(EXPECTED_RESULT);

        // when
        Object result = dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent);

        // then
        assertThat(result).isEqualTo(EXPECTED_RESULT);
        then(idempotentService).should().getCurrentStatus(PREFIXED_KEY);
        then(idempotentService).should().setIfAbsent(PREFIXED_KEY, IdempotentStatus.PROCESSING, TTL_SECONDS, "payload-body");
        then(idempotentService).should().saveStatus(PREFIXED_KEY, IdempotentStatus.SUCCESS, TTL_SECONDS);
    }

    @Test
    @DisplayName("keyPrefix가 있을 때 실패하면 접두어 키로 에러를 저장한다.")
    void handleIdempotency_WithKeyPrefix_OnFailure_SavesErrorByPrefixedKey() throws Throwable {
        // given
        RuntimeException expectedException = new RuntimeException("Business logic error");
        given(dbIdempotent.keyPrefix()).willReturn("STOCK_STORE:");
        given(joinPoint.getArgs()).willReturn(new Object[]{TEST_KEY});
        given(idempotentService.getCurrentStatus(PREFIXED_KEY)).willReturn(IdempotentStatus.NONE);
        given(idempotentService.setIfAbsent(PREFIXED_KEY, IdempotentStatus.PROCESSING, TTL_SECONDS, payload))
                .willReturn(true);
        given(joinPoint.proceed()).willThrow(expectedException);

        // when & then
        assertThatThrownBy(() -> dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Business logic error");

        then(idempotentService).should().saveStatus(PREFIXED_KEY, IdempotentStatus.FAIL, TTL_SECONDS);
        then(idempotentService).should().saveError(PREFIXED_KEY, "Business logic error", TTL_SECONDS);
    }

}
