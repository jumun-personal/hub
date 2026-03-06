package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hubRoute.application.HubRouteEventPublisher;
import com.jumunhasyeo.hub.hubRoute.application.command.RoutePairBuildTarget;
import com.jumunhasyeo.hub.hubRoute.application.dto.RoutePurpose;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus;
import com.jumunhasyeo.hub.hubRoute.domain.entity.RouteProvider;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteCreatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoutePairLifecycleService {

    private final HubRouteRepository hubRouteRepository;
    private final HubRouteEventPublisher hubRouteEventPublisher;
    private final HubRouteBuildJobService hubRouteBuildJobService;
    private final HubRoutePlanningService hubRoutePlanningService;

    @Value("${hub.route.build.event-recovery-delay:5m}")
    private String eventRecoveryDelay;

    @Value("${hub.route.refresh.interval:5m}")
    private String routeRefreshInterval;

    @Value("${hub.route.refresh.fallback-reconcile-delay:1m}")
    private String fallbackReconcileDelay;

    @Transactional
    public Optional<RoutePairBuildTarget> claimRoutePairBuild(List<UUID> routeIds) {
        List<UUID> distinctRouteIds = routeIds.stream().distinct().toList();
        List<HubRoute> routes = hubRouteRepository.findAllByIdsWithHubsForUpdate(distinctRouteIds);
        if (routes.size() != distinctRouteIds.size()) {
            return Optional.empty();
        }
        LocalDateTime staleBefore = LocalDateTime.now().minus(eventRecoveryDelay());
        boolean allClaimable = routes.stream().allMatch(route ->
                HubRouteStatus.PENDING.equals(route.getStatus())
                        || (HubRouteStatus.PROCESSING.equals(route.getStatus())
                        && route.getModifiedAt() != null
                        && route.getModifiedAt().isBefore(staleBefore))
        );
        if (!allClaimable) {
            return Optional.empty();
        }

        UUID processingToken = UUID.randomUUID();
        routes.forEach(route -> route.claimProcessing(processingToken));
        hubRouteRepository.saveAll(routes);
        HubRoute representative = routes.get(0);
        return Optional.of(new RoutePairBuildTarget(
                routes.stream().map(HubRoute::getRouteId).toList(),
                representative.getBuildHubId(),
                representative.getStartHub().getHubId(),
                representative.getStartHub().getCoordinate(),
                representative.getEndHub().getCoordinate(),
                resolvePurpose(representative.getStartHub(), representative.getEndHub()),
                routes.stream().mapToInt(HubRoute::getRetryCount).max().orElse(0),
                processingToken
        ));
    }

    @Transactional
    public Optional<RoutePairBuildTarget> claimRoutePairRefresh(List<UUID> routeIds) {
        List<UUID> distinctRouteIds = routeIds.stream().distinct().toList();
        List<HubRoute> routes = hubRouteRepository.findAllByIdsWithHubsForUpdate(distinctRouteIds);
        if (routes.size() != distinctRouteIds.size()) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minus(eventRecoveryDelay());
        boolean allRefreshable = routes.stream().allMatch(route ->
                route.isComplete()
                        && (route.getNextRefreshAt() == null || !route.getNextRefreshAt().isAfter(now))
                        && (route.getRefreshClaimedAt() == null || route.getRefreshClaimedAt().isBefore(staleBefore))
        );
        if (!allRefreshable) {
            return Optional.empty();
        }

        routes.forEach(route -> route.claimRefresh(now));
        hubRouteRepository.saveAll(routes);
        HubRoute representative = routes.get(0);
        return Optional.of(new RoutePairBuildTarget(
                routes.stream().map(HubRoute::getRouteId).toList(),
                representative.getBuildHubId(),
                representative.getStartHub().getHubId(),
                representative.getStartHub().getCoordinate(),
                representative.getEndHub().getCoordinate(),
                resolvePurpose(representative.getStartHub(), representative.getEndHub()),
                0,
                null
        ));
    }

    @Transactional
    public void completeRoutePairBuild(
            List<UUID> routeIds,
            UUID processingToken,
            RouteWeight routeWeight,
            RouteProvider provider,
            boolean fallback
    ) {
        List<HubRoute> routes = hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds);
        if (!isClaimOwned(routes, processingToken)) {
            return;
        }
        UUID buildHubId = routes.get(0).getBuildHubId();

        List<HubRoute> completedRoutes = routes.stream()
                .filter(route -> !route.isComplete())
                .toList();
        if (completedRoutes.isEmpty()) {
            return;
        }
        LocalDateTime nextRefreshAt = nextRefreshAt(routeIds, fallback);
        completedRoutes.forEach(route -> route.complete(routeWeight, provider, fallback, nextRefreshAt));
        hubRouteRepository.saveAll(completedRoutes);
        if (!completedRoutes.isEmpty()) {
            hubRouteEventPublisher.publishRouteCreatedEvent(
                    completedRoutes.stream()
                            .map(route -> HubRouteCreatedEvent.from(buildHubId, route))
                            .toList()
            );
        }

        if (buildHubId != null) {
            hubRouteBuildJobService.completePair(buildHubId)
                    .ifPresent(counter -> hubRoutePlanningService.finalizeIfTerminal(
                            counter,
                            "one or more hub routes failed"
                    ));
        }
    }

    @Transactional
    public void failRoutePairBuild(
            List<UUID> routeIds,
            UUID processingToken,
            String reason,
            int maxRetries,
            Duration retryBackoff
    ) {
        List<HubRoute> routes = hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds);
        if (!isClaimOwned(routes, processingToken)) {
            return;
        }
        UUID buildHubId = routes.get(0).getBuildHubId();

        LocalDateTime nextRetryAt = LocalDateTime.now().plus(retryBackoff);
        boolean finalFailed = routes.stream()
                .filter(route -> !route.isComplete())
                .map(route -> route.failOrRetry(reason, maxRetries, nextRetryAt))
                .reduce(false, Boolean::logicalOr);
        hubRouteRepository.saveAll(routes);

        if (finalFailed && buildHubId != null) {
            hubRouteBuildJobService.failPair(buildHubId, reason)
                    .ifPresent(counter -> hubRoutePlanningService.finalizeIfTerminal(counter, reason));
        }
    }

    @Transactional
    public void failRoutePairBuildPermanently(List<UUID> routeIds, UUID processingToken, String reason) {
        List<HubRoute> routes = hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds);
        if (!isClaimOwned(routes, processingToken)) {
            return;
        }
        UUID buildHubId = routes.get(0).getBuildHubId();

        routes.stream()
                .filter(route -> !route.isComplete())
                .forEach(route -> route.failPermanently(reason));
        hubRouteRepository.saveAll(routes);

        if (buildHubId != null) {
            hubRouteBuildJobService.failPair(buildHubId, reason)
                    .ifPresent(counter -> hubRoutePlanningService.finalizeIfTerminal(counter, reason));
        }
    }

    @Transactional
    public void deferRoutePairBuild(List<UUID> routeIds, UUID processingToken, String reason, Duration delay) {
        List<HubRoute> routes = hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds);
        if (!isClaimOwned(routes, processingToken)) {
            return;
        }
        LocalDateTime nextAttemptAt = LocalDateTime.now().plus(delay);
        routes.stream()
                .filter(route -> !route.isComplete())
                .forEach(route -> route.defer(nextAttemptAt, reason));
        hubRouteRepository.saveAll(routes);
    }

    @Transactional
    public void completeRoutePairRefresh(
            List<UUID> routeIds,
            RouteWeight routeWeight,
            RouteProvider provider,
            boolean fallback
    ) {
        List<HubRoute> routes = hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds);
        LocalDateTime nextRefreshAt = nextRefreshAt(routeIds, fallback);
        routes.forEach(route -> route.completeRefresh(routeWeight, provider, fallback, nextRefreshAt));
        hubRouteRepository.saveAll(routes);
    }

    @Transactional
    public void deferRoutePairRefresh(List<UUID> routeIds, String reason, Duration delay) {
        List<HubRoute> routes = hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds);
        LocalDateTime nextAttemptAt = LocalDateTime.now().plus(delay);
        routes.forEach(route -> route.deferRefresh(nextAttemptAt, reason));
        hubRouteRepository.saveAll(routes);
    }

    private RoutePurpose resolvePurpose(Hub from, Hub to) {
        if (from.isCenterHub() && to.isCenterHub()) {
            return RoutePurpose.CENTER_TO_CENTER;
        }
        if ((from.isBranchHub() && to.isCenterHub()) || (from.isCenterHub() && to.isBranchHub())) {
            return RoutePurpose.BRANCH_TO_CENTER;
        }
        return RoutePurpose.BRANCH_TO_BRANCH;
    }

    private LocalDateTime nextRefreshAt(List<UUID> routeIds) {
        Duration interval = routeRefreshInterval();
        long intervalSeconds = Math.max(1, interval.getSeconds());
        long nowEpochSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long nextCycleStart = Math.floorDiv(nowEpochSecond, intervalSeconds) * intervalSeconds
                + intervalSeconds;
        int pairHash = routeIds.stream()
                .map(UUID::toString)
                .sorted()
                .reduce("", String::concat)
                .hashCode();
        long slotOffset = Math.floorMod(pairHash, intervalSeconds);
        return LocalDateTime.ofEpochSecond(nextCycleStart + slotOffset, 0, ZoneOffset.UTC);
    }

    private LocalDateTime nextRefreshAt(List<UUID> routeIds, boolean fallback) {
        if (fallback) {
            String delay = fallbackReconcileDelay == null || fallbackReconcileDelay.isBlank()
                    ? "1m"
                    : fallbackReconcileDelay;
            return LocalDateTime.now().plus(DurationStyle.detectAndParse(delay));
        }
        return nextRefreshAt(routeIds);
    }

    private Duration eventRecoveryDelay() {
        return DurationStyle.detectAndParse(
                eventRecoveryDelay == null || eventRecoveryDelay.isBlank() ? "5m" : eventRecoveryDelay
        );
    }

    private Duration routeRefreshInterval() {
        return DurationStyle.detectAndParse(
                routeRefreshInterval == null || routeRefreshInterval.isBlank() ? "5m" : routeRefreshInterval
        );
    }

    private boolean isClaimOwned(List<HubRoute> routes, UUID processingToken) {
        return !routes.isEmpty()
                && processingToken != null
                && routes.stream().allMatch(route -> route.isClaimedBy(processingToken));
    }

}
