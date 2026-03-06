package com.jumunhasyeo.common.scheduler;

import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderAvailabilityService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteWorkLifecycle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hub.route.build.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class HubRouteBuildScheduler {

    private final HubRouteService hubRouteService;
    private final RouteProviderAvailabilityService routeProviderAvailabilityService;
    private final RouteWorkLifecycle routeWorkLifecycle;
    @Value("${hub.route.build.stale-processing-timeout:PT7M}")
    private String staleProcessingTimeout;

    @Scheduled(fixedDelayString = "${hub.route.build.fixed-delay-ms:500}")
    @SchedulerLock(
            name = "hubRouteBuildDispatch",
            lockAtLeastFor = "PT0.5S",
            lockAtMostFor = "${hub.route.build.lock-at-most-for:PT6M}"
    )
    public void buildPendingRoutes() {
        if (routeProviderAvailabilityService.isAllProvidersUnavailable()) {
            log.info("Skip hub route build. route providers are unavailable.");
            return;
        }

        List<UUID> routeIds = hubRouteService.findRunningJobBuildTargets(1, staleProcessingTimeout());
        if (routeIds.isEmpty()) {
            return;
        }

        List<UUID> routePairIds = hubRouteService.findRoutePairIds(routeIds.get(0));
        if (routePairIds.isEmpty()) {
            return;
        }

        routeWorkLifecycle.claimBuild(routePairIds).ifPresent(routeWorkLifecycle::build);
    }

    private Duration staleProcessingTimeout() {
        return DurationStyle.detectAndParse(staleProcessingTimeout);
    }
}
