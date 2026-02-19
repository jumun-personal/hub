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
import java.time.Duration;
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
        RateLimiterAutoConfiguration.class
})
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.kakaoRoute.slidingWindowSize=5",
        "resilience4j.circuitbreaker.instances.kakaoRoute.minimumNumberOfCalls=10",
        "resilience4j.circuitbreaker.instances.kakaoRoute.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.kakaoRoute.waitDurationInOpenState=60s",
        "resilience4j.circuitbreaker.instances.kakaoRoute.slidingWindowType=TIME_BASED",
        "resilience4j.circuitbreaker.instances.kakaoRoute.recordExceptions[0]=com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderTransientException",
        "resilience4j.circuitbreaker.instances.kakaoRoute.ignoreExceptions[0]=com.jumunhasyeo.hub.hubRoute.application.service.RouteRateLimitExceededException",
        "resilience4j.circuitbreaker.instances.kakaoRoute.ignoreExceptions[1]=com.jumunhasyeo.hub.hubRoute.application.service.RouteRequestRejectedException",
        "resilience4j.circuitbreaker.instances.kakaoRoute.ignoreExceptions[2]=com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderConfigurationException",
        "resilience4j.circuitbreaker.instances.naverRoute.slidingWindowSize=5",
        "resilience4j.circuitbreaker.instances.naverRoute.minimumNumberOfCalls=10",
        "resilience4j.circuitbreaker.instances.naverRoute.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.naverRoute.waitDurationInOpenState=60s",
        "resilience4j.circuitbreaker.instances.naverRoute.slidingWindowType=TIME_BASED",
        "resilience4j.circuitbreaker.instances.naverRoute.recordExceptions[0]=com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderTransientException",
        "resilience4j.circuitbreaker.instances.naverRoute.ignoreExceptions[0]=com.jumunhasyeo.hub.hubRoute.application.service.RouteRateLimitExceededException",
        "resilience4j.circuitbreaker.instances.naverRoute.ignoreExceptions[1]=com.jumunhasyeo.hub.hubRoute.application.service.RouteRequestRejectedException",
        "resilience4j.circuitbreaker.instances.naverRoute.ignoreExceptions[2]=com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderConfigurationException",
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
    @Autowired
    private DistributedRouteRateLimiter distributedRouteRateLimiter;

    @BeforeEach
    void setUp() {
        reset(kakaoStrategy, naverStrategy, routeProviderAvailabilityService, distributedRouteRateLimiter);
        given(distributedRouteRateLimiter.acquire(any()))
                .willReturn(DistributedRouteRateLimiter.RateLimitDecision.distributedAllowed());
        circuitBreakerRegistry.circuitBreaker("kakaoRoute").reset();
        circuitBreakerRegistry.circuitBreaker("naverRoute").reset();
    }

    @Test
    @DisplayName("Kakao 호출이 실패하면 Naver fallback을 수행하고 전체 retry는 수행하지 않는다")
    void getRouteInfo_whenKakaoFails_fallbackToNaverWithoutWholeRetry() {
        // given
        RouteWeightQuery query = routeWeightQuery();
        RouteWeightResult naverResult = routeWeightResult(MapProvider.NAVER);
        given(kakaoStrategy.getWeight(any())).willThrow(
                new RouteProviderTransientException(MapProvider.KAKAO, "kakao down")
        );
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
    @DisplayName("Primary 첫 일시 장애는 Naver로 전환하지 않고 Kakao 지연 재시도를 요청한다")
    void getRouteInfo_whenPrimaryFailsFirst_requestsDelayedPrimaryRetry() {
        // given
        RouteWeightQuery query = routeWeightQuery(ProviderHint.PRIMARY);
        given(kakaoStrategy.getWeight(any())).willThrow(
                new RouteProviderTransientException(MapProvider.KAKAO, "temporary timeout")
        );

        // when & then
        assertThatThrownBy(() -> routeWeightApiService.getRouteInfo(query))
                .isInstanceOf(RoutePrimaryRetryRequiredException.class);
        then(kakaoStrategy).should(times(1)).getWeight(any());
        then(naverStrategy).shouldHaveNoInteractions();
        then(routeProviderAvailabilityService).should(never()).markAllProvidersUnavailable(any());
    }

    @Test
    @DisplayName("Kakao 전역 토큰이 부족하면 Naver 전환이나 Provider 장애 게이트를 수행하지 않는다.")
    void getRouteInfo_whenGlobalRateLimitExhausted_delaysWithoutFallback() {
        // given
        RouteWeightQuery query = routeWeightQuery();
        given(distributedRouteRateLimiter.acquire(MapProvider.KAKAO)).willReturn(
                DistributedRouteRateLimiter.RateLimitDecision.denied(Duration.ofMillis(350))
        );

        // when & then
        assertThatThrownBy(() -> routeWeightApiService.getRouteInfo(query))
                .isInstanceOf(RouteRateLimitExceededException.class);
        then(kakaoStrategy).shouldHaveNoInteractions();
        then(naverStrategy).shouldHaveNoInteractions();
        then(routeProviderAvailabilityService).should(never()).markAllProvidersUnavailable(any());
        assertThat(circuitBreakerRegistry.circuitBreaker("kakaoRoute")
                .getMetrics()
                .getNumberOfFailedCalls()).isZero();
    }

    @Test
    @DisplayName("Kakao circuit이 OPEN이면 Kakao 호출 없이 Naver fallback을 수행한다.")
    void getRouteInfo_whenKakaoCircuitOpen_fallbackToNaverWithoutKakaoCall() {
        // given
        RouteWeightQuery query = routeWeightQuery(ProviderHint.PRIMARY);
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
        then(distributedRouteRateLimiter).should(never()).acquire(MapProvider.KAKAO);
        then(distributedRouteRateLimiter).should().acquire(MapProvider.NAVER);
        then(routeProviderAvailabilityService).should().clearAllProvidersUnavailable();
    }

    @Test
    @DisplayName("Naver fallback까지 실패하면 동기 retry 없이 일시 장애 예외를 전파한다")
    void getRouteInfo_whenFallbackFails_throwsWithoutSynchronousRetry() {
        // given
        RouteWeightQuery query = routeWeightQuery();
        circuitBreakerRegistry.circuitBreaker("kakaoRoute").transitionToOpenState();
        given(naverStrategy.getWeight(any())).willThrow(
                new RouteProviderTransientException(MapProvider.NAVER, "naver down")
        );

        // when & then
        assertThatThrownBy(() -> routeWeightApiService.getRouteInfo(query))
                .isInstanceOf(RouteProvidersUnavailableException.class);
        then(kakaoStrategy).should(never()).getWeight(any());
        then(naverStrategy).should(times(1)).getWeight(any());
        then(routeProviderAvailabilityService).should().markAllProvidersUnavailable(any());
        then(routeProviderAvailabilityService).should(never()).clearAllProvidersUnavailable();
    }

    @Test
    @DisplayName("두 circuit이 OPEN이면 토큰과 실제 API를 사용하지 않고 일시 장애를 전파한다")
    void getRouteInfo_whenBothCircuitsOpen_failsWithoutTokenOrApiCall() {
        // given
        RouteWeightQuery query = routeWeightQuery();
        circuitBreakerRegistry.circuitBreaker("kakaoRoute").transitionToOpenState();
        circuitBreakerRegistry.circuitBreaker("naverRoute").transitionToOpenState();

        // when & then
        assertThatThrownBy(() -> routeWeightApiService.getRouteInfo(query))
                .isInstanceOf(RouteProvidersUnavailableException.class);
        then(kakaoStrategy).should(never()).getWeight(any());
        then(naverStrategy).should(never()).getWeight(any());
        then(distributedRouteRateLimiter).shouldHaveNoInteractions();
        then(routeProviderAvailabilityService).should().markAllProvidersUnavailable(any());
        then(routeProviderAvailabilityService).should(never()).clearAllProvidersUnavailable();
    }

    @Test
    @DisplayName("Kakao와 Naver가 모두 실패해도 공급자별 한 번만 호출한다")
    void getRouteInfo_whenKakaoAndFallbackFail_callsEachProviderOnce() {
        // given
        RouteWeightQuery query = routeWeightQuery();
        given(kakaoStrategy.getWeight(any())).willThrow(
                new RouteProviderTransientException(MapProvider.KAKAO, "kakao down")
        );
        given(naverStrategy.getWeight(any())).willThrow(
                new RouteProviderTransientException(MapProvider.NAVER, "naver down")
        );

        // when & then
        assertThatThrownBy(() -> routeWeightApiService.getRouteInfo(query))
                .isInstanceOf(RouteProvidersUnavailableException.class);
        then(kakaoStrategy).should(times(1)).getWeight(any());
        then(naverStrategy).should(times(1)).getWeight(any());
        then(routeProviderAvailabilityService).should().markAllProvidersUnavailable(any());
    }

    @Test
    @DisplayName("잘못된 경로 요청은 Naver fallback이나 장애 게이트 없이 즉시 종료한다")
    void getRouteInfo_whenRequestRejected_failsWithoutFallback() {
        // given
        RouteWeightQuery query = routeWeightQuery();
        given(kakaoStrategy.getWeight(any())).willThrow(
                new RouteRequestRejectedException(MapProvider.KAKAO, "invalid coordinates")
        );

        // when & then
        assertThatThrownBy(() -> routeWeightApiService.getRouteInfo(query))
                .isInstanceOf(RouteRequestRejectedException.class);
        then(naverStrategy).shouldHaveNoInteractions();
        then(routeProviderAvailabilityService).should(never()).markAllProvidersUnavailable(any());
    }

    @Test
    @DisplayName("두 공급자의 인증 설정이 모두 잘못되면 지연 재시도 대상이 아닌 영구 오류로 분류한다")
    void getRouteInfo_whenBothProviderConfigurationsFail_rejectsPermanently() {
        // given
        RouteWeightQuery query = routeWeightQuery();
        given(kakaoStrategy.getWeight(any())).willThrow(
                new RouteProviderConfigurationException(MapProvider.KAKAO, "invalid kakao key")
        );
        given(naverStrategy.getWeight(any())).willThrow(
                new RouteProviderConfigurationException(MapProvider.NAVER, "invalid naver key")
        );

        // when & then
        assertThatThrownBy(() -> routeWeightApiService.getRouteInfo(query))
                .isInstanceOf(RouteResolutionRejectedException.class);
        then(kakaoStrategy).should(times(1)).getWeight(any());
        then(naverStrategy).should(times(1)).getWeight(any());
        then(routeProviderAvailabilityService).should(never()).markAllProvidersUnavailable(any());
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
        return routeWeightQuery(ProviderHint.ANY);
    }

    private static RouteWeightQuery routeWeightQuery(ProviderHint providerHint) {
        return new RouteWeightQuery(
                UUID.randomUUID(),
                Coordinate.of(37.5, 127.0),
                Coordinate.of(35.8, 128.6),
                RoutePurpose.CENTER_TO_CENTER,
                providerHint
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

        @Bean
        DistributedRouteRateLimiter distributedRouteRateLimiter() {
            return mock(DistributedRouteRateLimiter.class);
        }
    }
}
