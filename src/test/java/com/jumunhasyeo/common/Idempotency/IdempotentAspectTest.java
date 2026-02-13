package com.jumunhasyeo.common.Idempotency;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static com.jumunhasyeo.common.exception.ErrorCode.PROCESSING_CONFLICT_EXCEPTION;
import static com.jumunhasyeo.common.exception.ErrorCode.SUCCESS_CONFLICT_EXCEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class IdempotentAspectTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Idempotent idempotent;

    @InjectMocks
    private IdempotentAspect idempotentAspect;

    private static final String TEST_KEY = "test-idempotency-key";
    private static final String PREFIXED_KEY = "STOCK:" + TEST_KEY;
    private static final long PROCESSING_TTL_SECONDS = 300L;
    private static final long SUCCESS_TTL_SECONDS = 86_400L;
    private static final Duration PROCESSING_TTL = Duration.ofSeconds(PROCESSING_TTL_SECONDS);
    private static final Duration SUCCESS_TTL = Duration.ofSeconds(SUCCESS_TTL_SECONDS);
    private static final Object EXPECTED_RESULT = "test-result";

    @BeforeEach
    void setUp() {
        lenient().when(idempotent.processingTtlSeconds()).thenReturn(PROCESSING_TTL_SECONDS);
        lenient().when(idempotent.successTtlSeconds()).thenReturn(SUCCESS_TTL_SECONDS);
        lenient().when(idempotent.keyPrefix()).thenReturn("");
        lenient().when(joinPoint.getArgs()).thenReturn(new Object[]{TEST_KEY});
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Redis SET NX 성공 시 비즈니스 로직 실행 후 SUCCESS 상태를 TTL과 함께 저장한다.")
    void handleIdempotency_set_nx_success() throws Throwable {
        given(valueOperations.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING.name(), PROCESSING_TTL)).willReturn(true);
        given(joinPoint.proceed()).willReturn(EXPECTED_RESULT);

        Object result = idempotentAspect.handleIdempotency(joinPoint, idempotent);

        assertThat(result).isEqualTo(EXPECTED_RESULT);
        then(valueOperations).should().setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING.name(), PROCESSING_TTL);
        then(joinPoint).should().proceed();
        then(valueOperations).should().set(TEST_KEY, IdempotentStatus.SUCCESS.name(), SUCCESS_TTL);
    }

    @Test
    @DisplayName("Redis에 SUCCESS 키가 이미 있으면 SUCCESS_CONFLICT_EXCEPTION을 던진다.")
    void handleIdempotency_success_key_exists() throws Throwable {
        given(valueOperations.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING.name(), PROCESSING_TTL)).willReturn(false);
        given(valueOperations.get(TEST_KEY)).willReturn(IdempotentStatus.SUCCESS.name());

        assertThatThrownBy(() -> idempotentAspect.handleIdempotency(joinPoint, idempotent))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", SUCCESS_CONFLICT_EXCEPTION);

        then(joinPoint).should(never()).proceed();
    }

    @Test
    @DisplayName("Redis에 PROCESSING 키가 이미 있으면 PROCESSING_CONFLICT_EXCEPTION을 던진다.")
    void handleIdempotency_processing_key_exists() throws Throwable {
        given(valueOperations.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING.name(), PROCESSING_TTL)).willReturn(false);
        given(valueOperations.get(TEST_KEY)).willReturn(IdempotentStatus.PROCESSING.name());

        assertThatThrownBy(() -> idempotentAspect.handleIdempotency(joinPoint, idempotent))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PROCESSING_CONFLICT_EXCEPTION);

        then(joinPoint).should(never()).proceed();
    }

    @Test
    @DisplayName("비즈니스 로직 실패 시 Redis 키를 삭제해 재시도 가능하게 한다.")
    void handleIdempotency_business_logic_fails() throws Throwable {
        RuntimeException expectedException = new RuntimeException("Business logic error");
        given(valueOperations.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING.name(), PROCESSING_TTL)).willReturn(true);
        given(joinPoint.proceed()).willThrow(expectedException);

        assertThatThrownBy(() -> idempotentAspect.handleIdempotency(joinPoint, idempotent))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Business logic error");

        then(stringRedisTemplate).should().delete(TEST_KEY);
        then(valueOperations).should(never()).set(TEST_KEY, IdempotentStatus.SUCCESS.name(), SUCCESS_TTL);
    }

    @Test
    @DisplayName("keyPrefix가 있으면 접두어가 붙은 Redis 키를 사용한다.")
    void handleIdempotency_uses_key_prefix() throws Throwable {
        given(idempotent.keyPrefix()).willReturn("STOCK:");
        given(valueOperations.setIfAbsent(PREFIXED_KEY, IdempotentStatus.PROCESSING.name(), PROCESSING_TTL)).willReturn(true);
        given(joinPoint.proceed()).willReturn(EXPECTED_RESULT);

        Object result = idempotentAspect.handleIdempotency(joinPoint, idempotent);

        assertThat(result).isEqualTo(EXPECTED_RESULT);
        then(valueOperations).should().setIfAbsent(PREFIXED_KEY, IdempotentStatus.PROCESSING.name(), PROCESSING_TTL);
        then(valueOperations).should().set(PREFIXED_KEY, IdempotentStatus.SUCCESS.name(), SUCCESS_TTL);
    }

    @Test
    @DisplayName("멱등키가 비어 있으면 Redis에 접근하지 않고 요청을 거부한다.")
    void handleIdempotency_blank_key_rejected() {
        given(joinPoint.getArgs()).willReturn(new Object[]{" "});

        assertThatThrownBy(() -> idempotentAspect.handleIdempotency(joinPoint, idempotent))
                .isInstanceOf(BusinessException.class);

        then(stringRedisTemplate).should(never()).opsForValue();
    }

    @Test
    @DisplayName("비즈니스 로직 성공 후 SUCCESS 상태 저장이 실패해도 처리 결과는 반환한다.")
    void handleIdempotency_success_mark_fails_after_business_success() throws Throwable {
        given(valueOperations.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING.name(), PROCESSING_TTL)).willReturn(true);
        given(joinPoint.proceed()).willReturn(EXPECTED_RESULT);
        willThrow(new RuntimeException("redis unavailable"))
                .given(valueOperations)
                .set(TEST_KEY, IdempotentStatus.SUCCESS.name(), SUCCESS_TTL);

        Object result = idempotentAspect.handleIdempotency(joinPoint, idempotent);

        assertThat(result).isEqualTo(EXPECTED_RESULT);
        then(stringRedisTemplate).should(never()).delete(TEST_KEY);
    }
}
