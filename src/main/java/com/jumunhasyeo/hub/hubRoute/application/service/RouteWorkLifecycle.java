package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.command.RoutePairBuildTarget;
import com.jumunhasyeo.hub.hubRoute.application.dto.ProviderHint;
import com.jumunhasyeo.hub.hubRoute.application.dto.request.RouteWeightQuery;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.domain.entity.RouteProvider;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Route Pair의 claim부터 resolve, complete/defer/fail까지 소유하는 work lifecycle Module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteWorkLifecycle {

    private final RoutePairLifecycleService routePairLifecycleService;
    private final RouteProviderResolution routeProviderResolution;
    private final RouteProviderAvailabilityService routeProviderAvailabilityService;
    private final RouteDelayPolicy routeDelayPolicy;

    @Value("${hub.route.build.max-retries:3}")
    private int maxRetries;

    @Value("${hub.route.refresh.non-retryable-delay:30m}")
    private String nonRetryableRefreshDelay;

    public void build(List<UUID> routeIds) {
        if (routeProviderAvailabilityService.isAllProvidersUnavailable()) {
            log.info("Skip route pair build. providers are unavailable. routeIds={}", routeIds);
            return;
        }

        Optional<RoutePairBuildTarget> claimed = routePairLifecycleService.claimRoutePairBuild(routeIds);
        if (claimed.isEmpty()) {
            return;
        }

        RoutePairBuildTarget target = claimed.get();
        try {
            RouteWeightResult result = routeProviderResolution.resolve(toQuery(target));
            routePairLifecycleService.completeRoutePairBuild(
                    target.routeIds(),
                    RouteWeight.of(result.distanceKm(), result.durationMinutes()),
                    toRouteProvider(result.provider()),
                    result.fromFallback()
            );
        } catch (RouteRateLimitExceededException e) {
            log.debug("Route pair delayed by provider rate limit. routeIds={}", target.routeIds());
            routePairLifecycleService.deferRoutePairBuild(
                    target.routeIds(),
                    e.getMessage(),
                    routeDelayPolicy.rateLimitDelay(e.retryAfter())
            );
        } catch (RoutePrimaryRetryRequiredException e) {
            log.info("Retry Kakao primary route provider before fallback. routeIds={}", target.routeIds());
            routePairLifecycleService.failRoutePairBuild(
                    target.routeIds(),
                    e.getMessage(),
                    maxRetries,
                    routeDelayPolicy.primaryRetryDelay()
            );
        } catch (RouteRequestRejectedException | RouteResolutionRejectedException e) {
            log.warn("Route pair build rejected permanently. routeIds={}", target.routeIds(), e);
            routePairLifecycleService.failRoutePairBuildPermanently(target.routeIds(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Route pair build failed. routeIds={}", target.routeIds(), e);
            routePairLifecycleService.failRoutePairBuild(
                    target.routeIds(),
                    e.getMessage(),
                    maxRetries,
                    routeDelayPolicy.buildRetryDelay(target.retryCount())
            );
        }
    }

    public void refresh(List<UUID> routeIds) {
        if (routeProviderAvailabilityService.isAllProvidersUnavailable()) {
            return;
        }

        Optional<RoutePairBuildTarget> claimed = routePairLifecycleService.claimRoutePairRefresh(routeIds);
        if (claimed.isEmpty()) {
            return;
        }

        RoutePairBuildTarget target = claimed.get();
        try {
            RouteWeightResult result = routeProviderResolution.resolve(toQuery(target, ProviderHint.PRIMARY));
            routePairLifecycleService.completeRoutePairRefresh(
                    target.routeIds(),
                    RouteWeight.of(result.distanceKm(), result.durationMinutes()),
                    toRouteProvider(result.provider()),
                    result.fromFallback()
            );
        } catch (RouteRateLimitExceededException e) {
            routePairLifecycleService.deferRoutePairRefresh(
                    target.routeIds(),
                    e.getMessage(),
                    routeDelayPolicy.rateLimitDelay(e.retryAfter())
            );
        } catch (RoutePrimaryRetryRequiredException e) {
            routePairLifecycleService.deferRoutePairRefresh(
                    target.routeIds(),
                    e.getMessage(),
                    routeDelayPolicy.primaryRetryDelay()
            );
        } catch (RouteRequestRejectedException | RouteResolutionRejectedException e) {
            log.warn("Route pair refresh rejected. keep previous weight. routeIds={}", target.routeIds(), e);
            routePairLifecycleService.deferRoutePairRefresh(
                    target.routeIds(),
                    e.getMessage(),
                    DurationStyle.detectAndParse(nonRetryableRefreshDelay)
            );
        } catch (RuntimeException e) {
            log.warn("Route pair refresh failed. routeIds={}", target.routeIds(), e);
            routePairLifecycleService.deferRoutePairRefresh(
                    target.routeIds(),
                    e.getMessage(),
                    routeDelayPolicy.buildRetryDelay(target.retryCount())
            );
        }
    }

    private RouteWeightQuery toQuery(RoutePairBuildTarget target) {
        ProviderHint providerHint = target.retryCount() == 0 ? ProviderHint.PRIMARY : ProviderHint.ANY;
        return toQuery(target, providerHint);
    }

    private RouteWeightQuery toQuery(RoutePairBuildTarget target, ProviderHint providerHint) {
        return new RouteWeightQuery(
                target.startHubId(),
                target.startCoordinate(),
                target.endCoordinate(),
                target.purpose(),
                providerHint
        );
    }

    private RouteProvider toRouteProvider(com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider provider) {
        if (provider == null) {
            return RouteProvider.UNKNOWN;
        }
        return switch (provider) {
            case KAKAO -> RouteProvider.KAKAO;
            case NAVER -> RouteProvider.NAVER;
            case UNKNOWN -> RouteProvider.UNKNOWN;
        };
    }
}
