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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hub.route.build.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class HubRouteBuildScheduler {

    private final HubRouteService hubRouteService;
    private final RouteProviderAvailabilityService routeProviderAvailabilityService;
    private final RouteWorkLifecycle routeWorkLifecycle;
    @Qualifier("hubRouteBuildExecutor")
    private final ThreadPoolTaskExecutor hubRouteBuildExecutor;

    @Value("${hub.route.build.batch-size:2}")
    private int batchSize;

    @Value("${hub.route.build.stale-processing-timeout:PT5M}")
    private String staleProcessingTimeout;

    @Scheduled(fixedDelayString = "${hub.route.build.fixed-delay-ms:500}")
    @SchedulerLock(name = "hubRouteBuildDispatch", lockAtLeastFor = "PT0.5S", lockAtMostFor = "PT5S")
    public void buildPendingRoutes() {
        if (routeProviderAvailabilityService.isAllProvidersUnavailable()) {
            log.info("Skip hub route build. route providers are unavailable.");
            return;
        }

        int availableSlots = Math.max(0, hubRouteBuildExecutor.getMaxPoolSize() - hubRouteBuildExecutor.getActiveCount());
        int dispatchLimit = Math.min(availableSlots, batchSize);
        if (dispatchLimit == 0) {
            return;
        }

        List<UUID> routeIds = hubRouteService.findRunningJobBuildTargets(dispatchLimit * 2, staleProcessingTimeout());
        Set<String> claimedPairs = new HashSet<>();
        int dispatched = 0;
        for (UUID routeId : routeIds) {
            if (dispatched >= dispatchLimit) {
                break;
            }
            List<UUID> routePairIds = hubRouteService.findRoutePairIds(routeId);
            String pairKey = routePairIds.stream().map(UUID::toString).sorted().reduce("", String::concat);
            if (routePairIds.isEmpty() || !claimedPairs.add(pairKey)) {
                continue;
            }
            var target = routeWorkLifecycle.claimBuild(routePairIds);
            if (target.isPresent() && submit(target.orElseThrow())) {
                dispatched++;
            }
        }
    }

    private boolean submit(final com.jumunhasyeo.hub.hubRoute.application.command.RoutePairBuildTarget target) {
        try {
            hubRouteBuildExecutor.execute(() -> routeWorkLifecycle.build(target));
            return true;
        } catch (TaskRejectedException e) {
            log.warn("Hub route build executor rejected a claimed route pair. routeIds={}", target.routeIds());
            routeWorkLifecycle.releaseBuildClaim(target, "hub route build executor rejected task");
            return false;
        }
    }

    private Duration staleProcessingTimeout() {
        return DurationStyle.detectAndParse(staleProcessingTimeout);
    }
}
