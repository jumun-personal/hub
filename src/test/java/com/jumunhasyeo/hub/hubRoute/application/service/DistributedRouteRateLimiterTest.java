package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DistributedRouteRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private DistributedRouteRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new DistributedRouteRateLimiter(redisTemplate);
        ReflectionTestUtils.setField(rateLimiter, "kakaoPermitsPerSecond", 3);
        ReflectionTestUtils.setField(rateLimiter, "naverPermitsPerSecond", 5);
    }

    @Test
    @DisplayName("Lua Script가 0을 반환하면 분산 토큰 획득을 허용한다.")
    void acquire_whenTokenAvailable_allowsRequest() {
        given(executeScript()).willReturn(0L);

        DistributedRouteRateLimiter.RateLimitDecision result =
                rateLimiter.acquire(MapProvider.KAKAO);

        assertThat(result.allowed()).isTrue();
        assertThat(result.useLocalFallback()).isFalse();
    }

    @Test
    @DisplayName("Lua Script가 대기 시간을 반환하면 해당 시간 이후 재시도하도록 거절한다.")
    void acquire_whenTokenUnavailable_returnsRetryAfter() {
        given(executeScript()).willReturn(275L);

        DistributedRouteRateLimiter.RateLimitDecision result =
                rateLimiter.acquire(MapProvider.KAKAO);

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfter()).isEqualTo(Duration.ofMillis(275));
    }

    @Test
    @DisplayName("Redis 장애 시 분산 제한을 우회하고 보수적인 Local Rate Limiter로 전환한다.")
    void acquire_whenRedisUnavailable_usesLocalFallback() {
        given(executeScript()).willThrow(new IllegalStateException("redis down"));

        DistributedRouteRateLimiter.RateLimitDecision result =
                rateLimiter.acquire(MapProvider.NAVER);

        assertThat(result.allowed()).isTrue();
        assertThat(result.useLocalFallback()).isTrue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Long executeScript() {
        return (Long) redisTemplate.execute(
                any(RedisScript.class),
                org.mockito.ArgumentMatchers.<String>anyList(),
                any(),
                any(),
                any()
        );
    }
}
