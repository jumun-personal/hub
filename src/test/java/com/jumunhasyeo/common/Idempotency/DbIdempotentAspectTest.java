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
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DbIdempotentAspectTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private DbIdempotent dbIdempotent;

    @InjectMocks
    private DbIdempotentAspect dbIdempotentAspect;

    private static final String TEST_KEY = "test-idempotency-key";
    private static final String PREFIXED_KEY = "STOCK:" + TEST_KEY;
    private static final int TTL_DAYS = 1;
    private static final long TTL_SECONDS = TTL_DAYS * 24 * 3600L;
    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);
    private static final Object EXPECTED_RESULT = "test-result";

    @BeforeEach
    void setUp() {
        given(dbIdempotent.ttlDays()).willReturn(TTL_DAYS);
        given(dbIdempotent.keyPrefix()).willReturn("");
        given(joinPoint.getArgs()).willReturn(new Object[]{TEST_KEY});
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("Redis SET NX 성공 시 비즈니스 로직 실행 후 SUCCESS 상태를 TTL과 함께 저장한다.")
    void handleIdempotency_set_nx_success() throws Throwable {
        given(valueOperations.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING.name(), TTL)).willReturn(true);
        given(joinPoint.proceed()).willReturn(EXPECTED_RESULT);

        Object result = dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent);

        assertThat(result).isEqualTo(EXPECTED_RESULT);
        then(valueOperations).should().setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING.name(), TTL);
        then(joinPoint).should().proceed();
        then(valueOperations).should().set(TEST_KEY, IdempotentStatus.SUCCESS.name(), TTL);
    }

    @Test
    @DisplayName("Redis에 SUCCESS 키가 이미 있으면 SUCCESS_CONFLICT_EXCEPTION을 던진다.")
    void handleIdempotency_success_key_exists() throws Throwable {
        given(valueOperations.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING.name(), TTL)).willReturn(false);
        given(valueOperations.get(TEST_KEY)).willReturn(IdempotentStatus.SUCCESS.name());

        assertThatThrownBy(() -> dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", SUCCESS_CONFLICT_EXCEPTION);

        then(joinPoint).should(never()).proceed();
    }

    @Test
    @DisplayName("Redis에 PROCESSING 키가 이미 있으면 PROCESSING_CONFLICT_EXCEPTION을 던진다.")
    void handleIdempotency_processing_key_exists() throws Throwable {
        given(valueOperations.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING.name(), TTL)).willReturn(false);
        given(valueOperations.get(TEST_KEY)).willReturn(IdempotentStatus.PROCESSING.name());

        assertThatThrownBy(() -> dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PROCESSING_CONFLICT_EXCEPTION);

        then(joinPoint).should(never()).proceed();
    }

    @Test
    @DisplayName("비즈니스 로직 실패 시 Redis 키를 삭제해 재시도 가능하게 한다.")
    void handleIdempotency_business_logic_fails() throws Throwable {
        RuntimeException expectedException = new RuntimeException("Business logic error");
        given(valueOperations.setIfAbsent(TEST_KEY, IdempotentStatus.PROCESSING.name(), TTL)).willReturn(true);
        given(joinPoint.proceed()).willThrow(expectedException);

        assertThatThrownBy(() -> dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Business logic error");

        then(stringRedisTemplate).should().delete(TEST_KEY);
        then(valueOperations).should(never()).set(TEST_KEY, IdempotentStatus.SUCCESS.name(), TTL);
    }

    @Test
    @DisplayName("keyPrefix가 있으면 접두어가 붙은 Redis 키를 사용한다.")
    void handleIdempotency_uses_key_prefix() throws Throwable {
        given(dbIdempotent.keyPrefix()).willReturn("STOCK:");
        given(valueOperations.setIfAbsent(PREFIXED_KEY, IdempotentStatus.PROCESSING.name(), TTL)).willReturn(true);
        given(joinPoint.proceed()).willReturn(EXPECTED_RESULT);

        Object result = dbIdempotentAspect.handleIdempotency(joinPoint, dbIdempotent);

        assertThat(result).isEqualTo(EXPECTED_RESULT);
        then(valueOperations).should().setIfAbsent(PREFIXED_KEY, IdempotentStatus.PROCESSING.name(), TTL);
        then(valueOperations).should().set(PREFIXED_KEY, IdempotentStatus.SUCCESS.name(), TTL);
    }
}
