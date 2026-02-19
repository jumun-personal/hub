package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.dto.request.RouteWeightQuery;
import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import com.jumunhasyeo.hub.hubRoute.application.dto.ProviderHint;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.infrastructure.external.KakaoWeightRouteApiServiceImpl;
import com.jumunhasyeo.hub.hubRoute.infrastructure.external.NaverWeightRouteApiServiceImpl;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
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
    private final KakaoWeightRouteApiServiceImpl kakaoStrategy;
    private final NaverWeightRouteApiServiceImpl naverStrategy;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final RouteProviderAvailabilityService routeProviderAvailabilityService;
    private final DistributedRouteRateLimiter distributedRouteRateLimiter;

    @Override
    public RouteWeightResult getRouteInfo(RouteWeightQuery query) {
        try {
            RouteWeightResult result = resolveWithKakaoFallback(query);
            routeProviderAvailabilityService.clearAllProvidersUnavailable();
            return result;
        } catch (RouteRateLimitExceededException
                 | RouteRequestRejectedException
                 | RouteResolutionRejectedException
                 | RoutePrimaryRetryRequiredException e) {
            throw e;
        } catch (RuntimeException e) {
            routeProviderAvailabilityService.markAllProvidersUnavailable(e.getMessage());
            throw e;
        }
    }

    private RouteWeightResult resolveWithKakaoFallback(RouteWeightQuery query) {
        try {
            return callKakao(query);
        } catch (RouteRateLimitExceededException | RouteRequestRejectedException e) {
            throw e;
        } catch (CallNotPermittedException | RouteProviderConfigurationException e) {
            return fallbackToNaver(query, e);
        } catch (RuntimeException kakaoFailure) {
            if (query.providerHint() == ProviderHint.PRIMARY) {
                throw new RoutePrimaryRetryRequiredException(
                        "Kakao primary route provider retry required",
                        kakaoFailure
                );
            }
            return fallbackToNaver(query, kakaoFailure);
        }
    }

    private RouteWeightResult callKakao(RouteWeightQuery query) {
        return callProvider(
                MapProvider.KAKAO,
                KAKAO_ROUTE,
                () -> kakaoStrategy.getWeight(query)
        );
    }

    private RouteWeightResult fallbackToNaver(RouteWeightQuery query, RuntimeException kakaoFailure) {
        try {
            RouteWeightResult result = callProvider(
                    MapProvider.NAVER,
                    NAVER_ROUTE,
                    () -> naverStrategy.getWeight(query)
            );
            return new RouteWeightResult(
                    result.distanceKm(),
                    result.durationMinutes(),
                    result.provider(),
                    true
            );
        } catch (RouteRateLimitExceededException | RouteRequestRejectedException e) {
            throw e;
        } catch (RuntimeException naverFailure) {
            if (isProviderConfigurationFailure(kakaoFailure)
                    && isProviderConfigurationFailure(naverFailure)) {
                throw new RouteResolutionRejectedException(
                        "Both route providers rejected by configuration",
                        naverFailure
                );
            }
            naverFailure.addSuppressed(kakaoFailure);
            throw new RouteProvidersUnavailableException(
                    "Kakao and Naver route providers are unavailable",
                    naverFailure
            );
        }
    }

    private RouteWeightResult callProvider(
            MapProvider provider,
            String circuitBreakerName,
            Supplier<RouteWeightResult> apiCall
    ) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        Supplier<RouteWeightResult> protectedCall = CircuitBreaker.decorateSupplier(
                circuitBreaker,
                () -> callWithinRateLimit(provider, circuitBreakerName, apiCall)
        );
        return protectedCall.get();
    }

    private RouteWeightResult callWithinRateLimit(
            MapProvider provider,
            String rateLimiterName,
            Supplier<RouteWeightResult> apiCall
    ) {
        DistributedRouteRateLimiter.RateLimitDecision decision =
                distributedRouteRateLimiter.acquire(provider);
        if (!decision.allowed()) {
            throw new RouteRateLimitExceededException(provider, decision.retryAfter());
        }
        if (!decision.useLocalFallback()) {
            return apiCall.get();
        }

        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(rateLimiterName);
        Supplier<RouteWeightResult> localRateLimitedCall =
                RateLimiter.decorateSupplier(rateLimiter, apiCall);
        try {
            return localRateLimitedCall.get();
        } catch (RequestNotPermitted e) {
            throw new RouteRateLimitExceededException(provider);
        }
    }

    private boolean isProviderConfigurationFailure(RuntimeException exception) {
        return exception instanceof RouteProviderConfigurationException;
    }
}
