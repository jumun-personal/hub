package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.command.RoutePairBuildTarget;
import com.jumunhasyeo.hub.hubRoute.application.dto.ProviderHint;
import com.jumunhasyeo.hub.hubRoute.application.dto.request.RouteWeightQuery;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import com.jumunhasyeo.hub.hubRoute.domain.entity.RouteProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutePairBuildProcessor {

    private final HubRouteService hubRouteService;
    private final RouteWeightApiService routeWeightApiService;
    private final RouteProviderAvailabilityService routeProviderAvailabilityService;
    private final RouteDelayPolicy routeDelayPolicy;

    @Value("${hub.route.build.max-retries:3}")
    private int maxRetries;

    @Value("${hub.route.refresh.non-retryable-delay:30m}")
    private String nonRetryableRefreshDelay;

    public void process(List<UUID> routeIds) {
        if (routeProviderAvailabilityService.isAllProvidersUnavailable()) {
            log.info("Skip route pair build. providers are unavailable. routeIds={}", routeIds);
            return;
        }

        Optional<RoutePairBuildTarget> claimed = hubRouteService.claimRoutePairBuild(routeIds);
        if (claimed.isEmpty()) {
            return;
        }

        RoutePairBuildTarget target = claimed.get();
        try {
            RouteWeightResult result = routeWeightApiService.getRouteInfo(toQuery(target));
            hubRouteService.completeRoutePairBuild(
                    target.routeIds(),
                    RouteWeight.of(result.distanceKm(), result.durationMinutes()),
                    toRouteProvider(result.provider()),
                    result.fromFallback()
            );
        } catch (RouteRateLimitExceededException e) {
            log.debug("Route pair delayed by provider rate limit. routeIds={}", target.routeIds());
            hubRouteService.deferRoutePairBuild(
                    target.routeIds(),
                    e.getMessage(),
                    routeDelayPolicy.rateLimitDelay(e.retryAfter())
            );
        } catch (RoutePrimaryRetryRequiredException e) {
            log.info("Retry Kakao primary route provider before fallback. routeIds={}", target.routeIds());
            hubRouteService.failRoutePairBuild(
                    target.routeIds(),
                    e.getMessage(),
                    maxRetries,
                    routeDelayPolicy.primaryRetryDelay()
            );
        } catch (RouteRequestRejectedException | RouteResolutionRejectedException e) {
            log.warn("Route pair build rejected permanently. routeIds={}", target.routeIds(), e);
            hubRouteService.failRoutePairBuildPermanently(target.routeIds(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Route pair build failed. routeIds={}", target.routeIds(), e);
            hubRouteService.failRoutePairBuild(
                    target.routeIds(),
                    e.getMessage(),
                    maxRetries,
                    routeDelayPolicy.buildRetryDelay(target.retryCount())
            );
        }
    }

    public void processRefresh(List<UUID> routeIds) {
        if (routeProviderAvailabilityService.isAllProvidersUnavailable()) {
            return;
        }

        Optional<RoutePairBuildTarget> claimed = hubRouteService.claimRoutePairRefresh(routeIds);
        if (claimed.isEmpty()) {
            return;
        }

        RoutePairBuildTarget target = claimed.get();
        try {
            RouteWeightResult result = routeWeightApiService.getRouteInfo(
                    toQuery(target, ProviderHint.PRIMARY)
            );
            hubRouteService.completeRoutePairRefresh(
                    target.routeIds(),
                    RouteWeight.of(result.distanceKm(), result.durationMinutes()),
                    toRouteProvider(result.provider()),
                    result.fromFallback()
            );
        } catch (RouteRateLimitExceededException e) {
            hubRouteService.deferRoutePairRefresh(
                    target.routeIds(),
                    e.getMessage(),
                    routeDelayPolicy.rateLimitDelay(e.retryAfter())
            );
        } catch (RoutePrimaryRetryRequiredException e) {
            hubRouteService.deferRoutePairRefresh(
                    target.routeIds(),
                    e.getMessage(),
                    routeDelayPolicy.primaryRetryDelay()
            );
        } catch (RouteRequestRejectedException | RouteResolutionRejectedException e) {
            log.warn("Route pair refresh rejected. keep previous weight. routeIds={}", target.routeIds(), e);
            hubRouteService.deferRoutePairRefresh(
                    target.routeIds(),
                    e.getMessage(),
                    DurationStyle.detectAndParse(nonRetryableRefreshDelay)
            );
        } catch (RuntimeException e) {
            log.warn("Route pair refresh failed. routeIds={}", target.routeIds(), e);
            hubRouteService.deferRoutePairRefresh(
                    target.routeIds(),
                    e.getMessage(),
                    routeDelayPolicy.buildRetryDelay(target.retryCount())
            );
        }
    }

    private RouteWeightQuery toQuery(RoutePairBuildTarget target) {
        ProviderHint providerHint = target.retryCount() == 0
                ? ProviderHint.PRIMARY
                : ProviderHint.ANY;
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
