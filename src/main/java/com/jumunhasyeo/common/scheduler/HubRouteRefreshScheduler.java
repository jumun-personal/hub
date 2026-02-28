package com.jumunhasyeo.common.scheduler;

import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteWorkLifecycle;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hub.route.refresh.enabled", havingValue = "true", matchIfMissing = true)
public class HubRouteRefreshScheduler {

    private final HubRouteService hubRouteService;
    private final RouteProviderAvailabilityService routeProviderAvailabilityService;
    private final RouteWorkLifecycle routeWorkLifecycle;

    @Value("${hub.route.refresh.batch-size:10}")
    private int batchSize;

    @Value("${hub.route.refresh.stale-timeout:5m}")
    private String staleTimeout;

    @Scheduled(fixedDelayString = "${hub.route.refresh.poll-delay-ms:1000}")
    public void refreshDueRoutes() {
        if (routeProviderAvailabilityService.isAllProvidersUnavailable()) {
            return;
        }
        if (hubRouteService.hasActiveBuildWork()) {
            return;
        }

        List<UUID> candidates = hubRouteService.findRouteRefreshTargets(batchSize, staleTimeout());
        for (UUID candidate : candidates) {
            List<UUID> routePairIds = hubRouteService.findRoutePairIds(candidate);
            if (!routePairIds.isEmpty()) {
                routeWorkLifecycle.refresh(routePairIds);
            }
        }
    }

    private Duration staleTimeout() {
        return DurationStyle.detectAndParse(staleTimeout);
    }
}
