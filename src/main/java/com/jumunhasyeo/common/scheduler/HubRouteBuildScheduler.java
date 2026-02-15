package com.jumunhasyeo.common.scheduler;

import com.jumunhasyeo.hub.hubRoute.application.command.RouteBuildTarget;
import com.jumunhasyeo.hub.hubRoute.application.dto.ProviderHint;
import com.jumunhasyeo.hub.hubRoute.application.dto.request.RouteWeightQuery;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderAvailabilityService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteWeightApiService;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hub.route.build.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class HubRouteBuildScheduler {

    private final HubRouteService hubRouteService;
    private final RouteWeightApiService routeWeightApiService;
    private final RouteProviderAvailabilityService routeProviderAvailabilityService;

    @Value("${hub.route.build.batch-size:10}")
    private int batchSize;

    @Value("${hub.route.build.stale-processing-timeout:PT5M}")
    private String staleProcessingTimeout;

    @Value("${hub.route.build.retry-backoff:PT30S}")
    private String retryBackoff;

    @Value("${hub.route.build.max-retries:3}")
    private int maxRetries;

    @Scheduled(fixedDelayString = "${hub.route.build.fixed-delay-ms:1000}")
    public void buildPendingRoutes() {
        if (routeProviderAvailabilityService.isAllProvidersUnavailable()) {
            log.info("Skip hub route build. route providers are unavailable.");
            return;
        }

        List<UUID> routeIds = hubRouteService.claimRouteBuildTargets(batchSize, staleProcessingTimeout());
        for (UUID routeId : routeIds) {
            buildRoute(routeId);
        }
    }

    private void buildRoute(UUID routeId) {
        Optional<RouteBuildTarget> target = hubRouteService.getRouteBuildTarget(routeId);
        if (target.isEmpty()) {
            return;
        }

        try {
            RouteWeightResult result = routeWeightApiService.getRouteInfo(toQuery(target.get()));
            hubRouteService.completeRouteBuild(
                    routeId,
                    RouteWeight.of(result.distanceKm(), result.durationMinutes())
            );
        } catch (RuntimeException e) {
            log.warn("Hub route build failed. routeId={}", routeId, e);
            hubRouteService.failRouteBuild(routeId, e.getMessage(), maxRetries, retryBackoff());
        }
    }

    private RouteWeightQuery toQuery(RouteBuildTarget target) {
        return new RouteWeightQuery(
                target.startHubId(),
                target.startCoordinate(),
                target.endCoordinate(),
                target.purpose(),
                ProviderHint.ANY
        );
    }

    private Duration staleProcessingTimeout() {
        return DurationStyle.detectAndParse(staleProcessingTimeout);
    }

    private Duration retryBackoff() {
        return DurationStyle.detectAndParse(retryBackoff);
    }
}
