package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import com.jumunhasyeo.hub.hubRoute.application.dto.ProviderHint;
import com.jumunhasyeo.hub.hubRoute.application.dto.RoutePurpose;
import com.jumunhasyeo.hub.hubRoute.application.dto.request.RouteWeightQuery;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.infrastructure.external.KakaoWeightRouteApiServiceImpl;
import com.jumunhasyeo.hub.hubRoute.infrastructure.external.NaverWeightRouteApiServiceImpl;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;

@SpringJUnitConfig
@ContextConfiguration(classes = {
        ResilientRouteWeightApiService.class,
        ResilientRouteWeightApiServiceTest.TestConfig.class
})
@ImportAutoConfiguration({
        AopAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class,
        RetryAutoConfiguration.class,
        RateLimiterAutoConfiguration.class
})
@TestPropertySource(properties = {
        "resilience4j.retry.instances.routeResolve.maxAttempts=3",
        "resilience4j.retry.instances.routeResolve.waitDuration=1ms",
        "resilience4j.circuitbreaker.instances.kakaoRoute.slidingWindowSize=5",
        "resilience4j.circuitbreaker.instances.kakaoRoute.minimumNumberOfCalls=10",
        "resilience4j.circuitbreaker.instances.kakaoRoute.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.kakaoRoute.waitDurationInOpenState=60s",
        "resilience4j.circuitbreaker.instances.kakaoRoute.slidingWindowType=TIME_BASED",
        "resilience4j.circuitbreaker.instances.naverRoute.slidingWindowSize=5",
        "resilience4j.circuitbreaker.instances.naverRoute.minimumNumberOfCalls=10",
        "resilience4j.circuitbreaker.instances.naverRoute.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.naverRoute.waitDurationInOpenState=60s",
        "resilience4j.circuitbreaker.instances.naverRoute.slidingWindowType=TIME_BASED",
        "resilience4j.ratelimiter.instances.kakaoRoute.limitForPeriod=1000",
        "resilience4j.ratelimiter.instances.kakaoRoute.limitRefreshPeriod=1s",
        "resilience4j.ratelimiter.instances.kakaoRoute.timeoutDuration=0ms",
        "resilience4j.ratelimiter.instances.naverRoute.limitForPeriod=1000",
        "resilience4j.ratelimiter.instances.naverRoute.limitRefreshPeriod=1s",
        "resilience4j.ratelimiter.instances.naverRoute.timeoutDuration=0ms"
})
class ResilientRouteWeightApiServiceTest {

    @Autowired
    private RouteWeightApiService routeWeightApiService;

    @Autowired
    private KakaoWeightRouteApiServiceImpl kakaoStrategy;

    @Autowired
    private NaverWeightRouteApiServiceImpl naverStrategy;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @Autowired
    private RouteProviderAvailabilityService routeProviderAvailabilityService;

    @BeforeEach
    void setUp() {
        reset(kakaoStrategy, naverStrategy, routeProviderAvailabilityService);
        circuitBreakerRegistry.circuitBreaker("kakaoRoute").reset();
        circuitBreakerRegistry.circuitBreaker("naverRoute").reset();
    }

    @Test
    @DisplayName("Kakao 호출이 실패하면 Naver fallback을 수행하고 전체 retry는 수행하지 않는다")
    void getRouteInfo_whenKakaoFails_fallbackToNaverWithoutWholeRetry() {
        // given
        RouteWeightQuery query = routeWeightQuery();
        RouteWeightResult naverResult = routeWeightResult(MapProvider.NAVER);
        given(kakaoStrategy.getWeight(any())).willThrow(new RuntimeException("kakao down"));
        given(naverStrategy.getWeight(any())).willReturn(naverResult);

        // when
        RouteWeightResult result = routeWeightApiService.getRouteInfo(query);

        // then
        assertThat(result.provider()).isEqualTo(MapProvider.NAVER);
        assertThat(result.fromFallback()).isTrue();
        then(kakaoStrategy).should(times(1)).getWeight(any());
        then(naverStrategy).should().getWeight(any());
        assertThat(circuitBreakerRegistry.circuitBreaker("kakaoRoute")
                .getMetrics()
                .getNumberOfFailedCalls())
                .isEqualTo(1);
        assertThat(circuitBreakerRegistry.circuitBreaker("naverRoute")
                .getMetrics()
                .getNumberOfFailedCalls())
                .isEqualTo(0);
        then(routeProviderAvailabilityService).should().clearAllProvidersUnavailable();
        then(routeProviderAvailabilityService).should(never()).markAllProvidersUnavailable(any());
    }

    @Test
    @DisplayName("Kakao circuit이 OPEN이면 Kakao 호출 없이 Naver fallback을 수행한다.")
    void getRouteInfo_whenKakaoCircuitOpen_fallbackToNaverWithoutKakaoCall() {
        // given
        RouteWeightQuery query = routeWeightQuery();
        RouteWeightResult naverResult = routeWeightResult(MapProvider.NAVER);
        circuitBreakerRegistry.circuitBreaker("kakaoRoute").transitionToOpenState();
        given(naverStrategy.getWeight(any())).willReturn(naverResult);

        // when
        RouteWeightResult result = routeWeightApiService.getRouteInfo(query);

        // then
        assertThat(result.provider()).isEqualTo(MapProvider.NAVER);
        assertThat(result.fromFallback()).isTrue();
        then(kakaoStrategy).should(never()).getWeight(any());
        then(naverStrategy).should().getWeight(any());
        then(routeProviderAvailabilityService).should().clearAllProvidersUnavailable();
    }

    @Test
    @DisplayName("Naver fallback까지 실패하면 전체 흐름을 retry한 뒤 최종 예외가 전파된다")
    void getRouteInfo_whenFallbackFails_retriesWholeFlowThenThrowsException() {
        // given
        RouteWeightQuery query = routeWeightQuery();
        circuitBreakerRegistry.circuitBreaker("kakaoRoute").transitionToOpenState();
        given(naverStrategy.getWeight(any())).willThrow(new RuntimeException("naver down"));

        // when & then
        assertThatThrownBy(() -> routeWeightApiService.getRouteInfo(query))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("naver down");
        then(kakaoStrategy).should(never()).getWeight(any());
        then(naverStrategy).should(times(3)).getWeight(any());
        then(routeProviderAvailabilityService).should().markAllProvidersUnavailable(any());
        then(routeProviderAvailabilityService).should(never()).clearAllProvidersUnavailable();
    }

    @Test
    @DisplayName("Naver circuit이 OPEN이면 Naver 실제 호출 없이 전체 흐름을 retry한 뒤 최종 예외가 전파된다")
    void getRouteInfo_whenNaverCircuitOpen_retriesWithoutNaverCallThenThrowsException() {
        // given
        RouteWeightQuery query = routeWeightQuery();
        circuitBreakerRegistry.circuitBreaker("kakaoRoute").transitionToOpenState();
        circuitBreakerRegistry.circuitBreaker("naverRoute").transitionToOpenState();

        // when & then
        assertThatThrownBy(() -> routeWeightApiService.getRouteInfo(query))
                .isInstanceOf(RuntimeException.class);
        then(kakaoStrategy).should(never()).getWeight(any());
        then(naverStrategy).should(never()).getWeight(any());
        then(routeProviderAvailabilityService).should().markAllProvidersUnavailable(any());
        then(routeProviderAvailabilityService).should(never()).clearAllProvidersUnavailable();
    }

    @Test
    @DisplayName("Naver fallback 실패 시 Kakao부터 시작하는 전체 경로 조회를 retry한다")
    void getRouteInfo_whenKakaoAndFallbackFail_retriesFromKakao() {
        // given
        RouteWeightQuery query = routeWeightQuery();
        given(kakaoStrategy.getWeight(any())).willThrow(new RuntimeException("kakao down"));
        given(naverStrategy.getWeight(any())).willThrow(new RuntimeException("naver down"));

        // when & then
        assertThatThrownBy(() -> routeWeightApiService.getRouteInfo(query))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("naver down");
        then(kakaoStrategy).should(times(3)).getWeight(any());
        then(naverStrategy).should(times(3)).getWeight(any());
        then(routeProviderAvailabilityService).should().markAllProvidersUnavailable(any());
    }

    @Test
    @DisplayName("Kakao와 Naver TPS는 독립 RateLimiter 설정으로 제어한다")
    void routeProviders_useIndependentRateLimitersForTpsControl() {
        // when
        int kakaoLimitForPeriod = rateLimiterRegistry.rateLimiter("kakaoRoute")
                .getRateLimiterConfig()
                .getLimitForPeriod();
        int naverLimitForPeriod = rateLimiterRegistry.rateLimiter("naverRoute")
                .getRateLimiterConfig()
                .getLimitForPeriod();

        // then
        assertThat(kakaoLimitForPeriod).isEqualTo(1000);
        assertThat(naverLimitForPeriod).isEqualTo(1000);
    }

    @Test
    @DisplayName("Kakao와 Naver circuit은 독립적인 시간 기반 window로 동작한다")
    void routeProviders_useIndependentTimeBasedCircuitBreakers() {
        // when & then
        assertThat(circuitBreakerRegistry.circuitBreaker("kakaoRoute")
                .getCircuitBreakerConfig()
                .getSlidingWindowType()
                .name())
                .isEqualTo("TIME_BASED");
        assertThat(circuitBreakerRegistry.circuitBreaker("naverRoute")
                .getCircuitBreakerConfig()
                .getSlidingWindowType()
                .name())
                .isEqualTo("TIME_BASED");
    }

    private static RouteWeightQuery routeWeightQuery() {
        return new RouteWeightQuery(
                UUID.randomUUID(),
                Coordinate.of(37.5, 127.0),
                Coordinate.of(35.8, 128.6),
                RoutePurpose.CENTER_TO_CENTER,
                ProviderHint.ANY
        );
    }

    private static RouteWeightResult routeWeightResult(MapProvider provider) {
        return new RouteWeightResult(
                BigDecimal.valueOf(10),
                20,
                provider,
                false
        );
    }

    @Configuration
    static class TestConfig {

        @Bean
        KakaoWeightRouteApiServiceImpl kakaoStrategy() {
            return mock(KakaoWeightRouteApiServiceImpl.class);
        }

        @Bean
        NaverWeightRouteApiServiceImpl naverStrategy() {
            return mock(NaverWeightRouteApiServiceImpl.class);
        }

        @Bean
        @Primary
        RouteProviderAvailabilityService routeProviderAvailabilityService() {
            return mock(RouteProviderAvailabilityService.class);
        }
    }
}
