package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hubRoute.application.HubRouteEventPublisher;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;
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

    private final HubRepository hubRepository;
    private final HubRouteRepository hubRouteRepository;
    private final HubRouteEventPublisher hubRouteEventPublisher;

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

        routes.forEach(HubRoute::claimProcessing);
        hubRouteRepository.saveAll(routes);
        HubRoute representative = routes.get(0);
        return Optional.of(new RoutePairBuildTarget(
                routes.stream().map(HubRoute::getRouteId).toList(),
                representative.getBuildHubId(),
                representative.getStartHub().getHubId(),
                representative.getStartHub().getCoordinate(),
                representative.getEndHub().getCoordinate(),
                resolvePurpose(representative.getStartHub(), representative.getEndHub()),
                routes.stream().mapToInt(HubRoute::getRetryCount).max().orElse(0)
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
                0
        ));
    }

    @Transactional
    public void completeRoutePairBuild(
            List<UUID> routeIds,
            RouteWeight routeWeight,
            RouteProvider provider,
            boolean fallback
    ) {
        List<HubRoute> routes = hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds);
        if (routes.isEmpty()) {
            return;
        }
        UUID buildHubId = routes.get(0).getBuildHubId();
        if (buildHubId != null) {
            hubRouteRepository.lockBuildHub(buildHubId);
        }

        List<HubRoute> completedRoutes = routes.stream()
                .filter(route -> !route.isComplete())
                .toList();
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

        if (buildHubId != null && !hubRouteRepository.hasIncompleteRoutes(buildHubId)) {
            hubRouteEventPublisher.publishRouteBuildCompleted(buildCompletedCommand(buildHubId));
        }
    }

    @Transactional
    public void failRoutePairBuild(
            List<UUID> routeIds,
            String reason,
            int maxRetries,
            Duration retryBackoff
    ) {
        List<HubRoute> routes = hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds);
        if (routes.isEmpty()) {
            return;
        }
        UUID buildHubId = routes.get(0).getBuildHubId();
        if (buildHubId != null) {
            hubRouteRepository.lockBuildHub(buildHubId);
        }

        LocalDateTime nextRetryAt = LocalDateTime.now().plus(retryBackoff);
        boolean finalFailed = routes.stream()
                .filter(route -> !route.isComplete())
                .map(route -> route.failOrRetry(reason, maxRetries, nextRetryAt))
                .reduce(false, Boolean::logicalOr);
        hubRouteRepository.saveAll(routes);

        if (finalFailed && buildHubId != null) {
            hubRouteEventPublisher.publishRouteBuildFailed(buildHubId, reason);
        }
    }

    @Transactional
    public void failRoutePairBuildPermanently(List<UUID> routeIds, String reason) {
        List<HubRoute> routes = hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds);
        if (routes.isEmpty()) {
            return;
        }
        UUID buildHubId = routes.get(0).getBuildHubId();
        if (buildHubId != null) {
            hubRouteRepository.lockBuildHub(buildHubId);
        }

        routes.stream()
                .filter(route -> !route.isComplete())
                .forEach(route -> route.failPermanently(reason));
        hubRouteRepository.saveAll(routes);

        if (buildHubId != null) {
            hubRouteEventPublisher.publishRouteBuildFailed(buildHubId, reason);
        }
    }

    @Transactional
    public void deferRoutePairBuild(List<UUID> routeIds, String reason, Duration delay) {
        List<HubRoute> routes = hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds);
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

    private BuildRouteCommand buildCompletedCommand(UUID buildHubId) {
        Hub hub = hubRepository.findByIdIncludingCreating(buildHubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HUB_NOT_FOUND));
        UUID centerHubId = hub.isBranchHub()
                ? hub.getCenterHubs().stream().findFirst().map(Hub::getHubId).orElse(null)
                : null;
        return new BuildRouteCommand(centerHubId, hub.getHubId(), hub.getName(), hub.getAddress(), hub.getHubType());
    }
}
