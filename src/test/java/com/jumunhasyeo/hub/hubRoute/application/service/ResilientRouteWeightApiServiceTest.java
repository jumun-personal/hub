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
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@SpringJUnitConfig
@ContextConfiguration(classes = {
        ResilientRouteWeightApiService.class,
        ResilientRouteWeightApiServiceTest.TestConfig.class
})
@ImportAutoConfiguration({
        AopAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class,
        RetryAutoConfiguration.class
})
@TestPropertySource(properties = {
        "resilience4j.retry.instances.kakaoRoute.maxAttempts=3",
        "resilience4j.retry.instances.kakaoRoute.waitDuration=1ms",
        "resilience4j.retry.instances.naverRoute.maxAttempts=1",
        "resilience4j.retry.instances.naverRoute.waitDuration=1ms",
        "resilience4j.circuitbreaker.instances.kakaoRoute.slidingWindowSize=5",
        "resilience4j.circuitbreaker.instances.kakaoRoute.minimumNumberOfCalls=5",
        "resilience4j.circuitbreaker.instances.kakaoRoute.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.kakaoRoute.waitDurationInOpenState=60s",
        "resilience4j.circuitbreaker.instances.naverRoute.slidingWindowSize=2",
        "resilience4j.circuitbreaker.instances.naverRoute.minimumNumberOfCalls=2",
        "resilience4j.circuitbreaker.instances.naverRoute.failureRateThreshold=50"
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

    @BeforeEach
    void setUp() {
        reset(kakaoStrategy, naverStrategy);
        circuitBreakerRegistry.circuitBreaker("kakaoRoute").reset();
        circuitBreakerRegistry.circuitBreaker("naverRoute").reset();
    }

    @Test
    @DisplayName("Kakao 호출이 계속 실패하면 retry 이후 Naver fallback을 수행한다.")
    void getRouteInfo_whenKakaoFails_retriesThenFallbackToNaver() {
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
        then(kakaoStrategy).should(org.mockito.Mockito.times(3)).getWeight(any());
        then(naverStrategy).should().getWeight(any());
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
    }

    @Test
    @DisplayName("Naver fallback까지 실패하면 최종 예외가 전파된다.")
    void getRouteInfo_whenFallbackFails_throwsException() {
        // given
        RouteWeightQuery query = routeWeightQuery();
        circuitBreakerRegistry.circuitBreaker("kakaoRoute").transitionToOpenState();
        given(naverStrategy.getWeight(any())).willThrow(new RuntimeException("naver down"));

        // when & then
        assertThatThrownBy(() -> routeWeightApiService.getRouteInfo(query))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("naver down");
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
    }
}
