package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.dto.request.RouteWeightQuery;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.infrastructure.external.KakaoWeightRouteApiServiceImpl;
import com.jumunhasyeo.hub.hubRoute.infrastructure.external.NaverWeightRouteApiServiceImpl;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
@Primary
@RequiredArgsConstructor
public class ResilientRouteWeightApiService implements RouteWeightApiService {

    private static final String KAKAO_ROUTE = "kakaoRoute";
    private static final String NAVER_ROUTE = "naverRoute";
    private static final String ROUTE_RESOLVE = "routeResolve";

    private final KakaoWeightRouteApiServiceImpl kakaoStrategy;
    private final NaverWeightRouteApiServiceImpl naverStrategy;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final RetryRegistry retryRegistry;
    private final RouteProviderAvailabilityService routeProviderAvailabilityService;

    @Override
    public RouteWeightResult getRouteInfo(RouteWeightQuery query) {
        Retry retry = retryRegistry.retry(ROUTE_RESOLVE);
        Supplier<RouteWeightResult> routeResolve = Retry.decorateSupplier(
                retry,
                () -> resolveWithKakaoFallback(query)
        );
        try {
            RouteWeightResult result = routeResolve.get();
            routeProviderAvailabilityService.clearAllProvidersUnavailable();
            return result;
        } catch (RuntimeException e) {
            routeProviderAvailabilityService.markAllProvidersUnavailable(e.getMessage());
            throw e;
        }
    }

    private RouteWeightResult resolveWithKakaoFallback(RouteWeightQuery query) {
        try {
            return callKakao(query);
        } catch (RuntimeException e) {
            return fallbackToNaver(query);
        }
    }

    private RouteWeightResult callKakao(RouteWeightQuery query) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(KAKAO_ROUTE);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(KAKAO_ROUTE);
        Supplier<RouteWeightResult> protectedKakaoCall = CircuitBreaker.decorateSupplier(
                circuitBreaker,
                () -> kakaoStrategy.getWeight(query)
        );
        Supplier<RouteWeightResult> rateLimitedKakaoCall = RateLimiter.decorateSupplier(rateLimiter, protectedKakaoCall);
        return rateLimitedKakaoCall.get();
    }

    private RouteWeightResult fallbackToNaver(RouteWeightQuery query) {
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(NAVER_ROUTE);
        Supplier<RouteWeightResult> naverCall = RateLimiter.decorateSupplier(
                rateLimiter,
                () -> naverStrategy.getWeight(query)
        );
        RouteWeightResult result = naverCall.get();
        return new RouteWeightResult(
                result.distanceKm(),
                result.durationMinutes(),
                result.provider(),
                true
        );
    }
}
