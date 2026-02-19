package com.jumunhasyeo.hub.hubRoute.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RouteDelayPolicyTest {

    private RouteDelayPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new RouteDelayPolicy();
        ReflectionTestUtils.setField(policy, "initialRetryBackoff", "10s");
        ReflectionTestUtils.setField(policy, "primaryRetryDelay", "5s");
        ReflectionTestUtils.setField(policy, "maxRetryBackoff", "2m");
        ReflectionTestUtils.setField(policy, "retryJitterMax", "0ms");
        ReflectionTestUtils.setField(policy, "defaultRateLimitDelay", "1s");
        ReflectionTestUtils.setField(policy, "rateLimitJitterMax", "0ms");
    }

    @Test
    @DisplayName("Kakao Primary 첫 실패는 짧은 지연 후 같은 공급자를 다시 시도한다.")
    void primaryRetryDelay_usesShortDelay() {
        assertThat(policy.primaryRetryDelay()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("일시 장애 재시도는 실패 횟수에 따라 지수 Backoff를 적용하고 최대값으로 제한한다.")
    void buildRetryDelay_appliesCappedExponentialBackoff() {
        assertThat(policy.buildRetryDelay(0)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.buildRetryDelay(1)).isEqualTo(Duration.ofSeconds(20));
        assertThat(policy.buildRetryDelay(2)).isEqualTo(Duration.ofSeconds(40));
        assertThat(policy.buildRetryDelay(10)).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("Rate Limit은 공급자가 전달한 Retry-After를 우선 사용한다.")
    void rateLimitDelay_usesProviderRetryAfter() {
        assertThat(policy.rateLimitDelay(Duration.ofSeconds(7)))
                .isEqualTo(Duration.ofSeconds(7));
    }
}
