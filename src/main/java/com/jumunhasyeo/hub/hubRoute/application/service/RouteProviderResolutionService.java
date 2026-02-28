package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import com.jumunhasyeo.hub.hubRoute.application.dto.ProviderHint;
import com.jumunhasyeo.hub.hubRoute.application.dto.request.RouteWeightQuery;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderMetrics.RouteCallPhase.FALLBACK;
import static com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderMetrics.RouteCallPhase.INITIAL;
import static com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderMetrics.RouteCallPhase.RETRY;

@Service
@Primary
public class RouteProviderResolutionService implements RouteProviderResolution {

    private static final Map<MapProvider, String> RESILIENCE_NAMES = Map.of(
            MapProvider.KAKAO, "kakaoRoute",
            MapProvider.NAVER, "naverRoute"
    );

    private final Map<MapProvider, RouteWeightStrategy> strategies;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final RouteProviderAvailabilityService routeProviderAvailabilityService;
    private final DistributedRouteRateLimiter distributedRouteRateLimiter;
    private final RouteProviderMetrics routeProviderMetrics;

    public RouteProviderResolutionService(
            List<RouteWeightStrategy> strategies,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            RouteProviderAvailabilityService routeProviderAvailabilityService,
            DistributedRouteRateLimiter distributedRouteRateLimiter,
            RouteProviderMetrics routeProviderMetrics
    ) {
        EnumMap<MapProvider, RouteWeightStrategy> byProvider = new EnumMap<>(MapProvider.class);
        for (RouteWeightStrategy strategy : strategies) {
            RouteWeightStrategy previous = byProvider.put(strategy.provider(), strategy);
            if (previous != null) {
                throw new IllegalStateException("Duplicate route provider adapter: " + strategy.provider());
            }
        }
        requireAdapter(byProvider, MapProvider.KAKAO);
        requireAdapter(byProvider, MapProvider.NAVER);
        this.strategies = Map.copyOf(byProvider);
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.routeProviderAvailabilityService = routeProviderAvailabilityService;
        this.distributedRouteRateLimiter = distributedRouteRateLimiter;
        this.routeProviderMetrics = routeProviderMetrics;
    }

    @Override
    public RouteWeightResult resolve(RouteWeightQuery query) {
        try {
            RouteWeightResult result = resolveWithFallback(query);
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

    private RouteWeightResult resolveWithFallback(RouteWeightQuery query) {
        try {
            return callProvider(
                    MapProvider.KAKAO,
                    query,
                    query.providerHint() == ProviderHint.ANY ? RETRY : INITIAL
            );
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

    private RouteWeightResult fallbackToNaver(RouteWeightQuery query, RuntimeException kakaoFailure) {
        try {
            RouteWeightResult result = callProvider(MapProvider.NAVER, query, FALLBACK);
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
            RouteWeightQuery query,
            RouteProviderMetrics.RouteCallPhase phase
    ) {
        String resilienceName = RESILIENCE_NAMES.get(provider);
        RouteWeightStrategy strategy = strategies.get(provider);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(resilienceName);
        Supplier<RouteWeightResult> protectedCall = CircuitBreaker.decorateSupplier(
                circuitBreaker,
                () -> callWithinRateLimit(
                        provider,
                        resilienceName,
                        () -> routeProviderMetrics.observe(provider, phase, () -> strategy.getWeight(query))
                )
        );
        return protectedCall.get();
    }

    private RouteWeightResult callWithinRateLimit(
            MapProvider provider,
            String rateLimiterName,
            Supplier<RouteWeightResult> apiCall
    ) {
        DistributedRouteRateLimiter.RateLimitDecision decision = distributedRouteRateLimiter.acquire(provider);
        if (!decision.allowed()) {
            throw new RouteRateLimitExceededException(provider, decision.retryAfter());
        }
        if (!decision.useLocalFallback()) {
            return apiCall.get();
        }

        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(rateLimiterName);
        try {
            return RateLimiter.decorateSupplier(rateLimiter, apiCall).get();
        } catch (RequestNotPermitted e) {
            throw new RouteRateLimitExceededException(provider);
        }
    }

    private static void requireAdapter(Map<MapProvider, RouteWeightStrategy> strategies, MapProvider provider) {
        if (!strategies.containsKey(provider)) {
            throw new IllegalStateException("Missing route provider adapter: " + provider);
        }
    }

    private boolean isProviderConfigurationFailure(RuntimeException exception) {
        return exception instanceof RouteProviderConfigurationException;
    }
}
