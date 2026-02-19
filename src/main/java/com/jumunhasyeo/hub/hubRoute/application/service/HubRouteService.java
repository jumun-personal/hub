package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hubRoute.application.HubRouteEventPublisher;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;
import com.jumunhasyeo.hub.hubRoute.application.command.RouteBuildTarget;
import com.jumunhasyeo.hub.hubRoute.application.command.RoutePairBuildTarget;
import com.jumunhasyeo.hub.hubRoute.application.dto.RoutePurpose;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.HubRouteRes;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus;
import com.jumunhasyeo.hub.hubRoute.domain.entity.RouteProvider;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildRequestedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteCreatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDeletedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import com.jumunhasyeo.hub.hubRoute.domain.service.HubRouteDomainService;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HubRouteService {
    private final HubRepository hubRepository;
    private final HubRouteRepository hubRouteRepository;
    private final HubRouteDomainService hubRouteDomainService;
    private final HubRouteEventPublisher hubRouteEventPublisher;

    @Value("${hub.route.build.event-recovery-delay:5m}")
    private String eventRecoveryDelay;

    @Value("${hub.route.refresh.interval:5m}")
    private String routeRefreshInterval;

    @Value("${hub.route.refresh.fallback-reconcile-delay:1m}")
    private String fallbackReconcileDelay;

    /**
     * 새로운 Hub 생성 시 경로 자동 생성
     */
    @Transactional
    public void buildRoutesForNewHub(BuildRouteCommand command) {
        Set<HubRoute> hubRoutes = new HashSet<>();
        HubType type = command.type();
        if (type == HubType.CENTER) {
            hubRoutes.addAll(buildForCenter(command));
        } else if (type == HubType.BRANCH) {
            hubRoutes.addAll(buildForBranch(command));
        }

        if (!hubRoutes.isEmpty()) {
            hubRouteEventPublisher.publishRouteBuildRequested(toBuildRequestedEvents(command.hubId(), hubRoutes));
        } else if (!hubRouteRepository.hasIncompleteRoutes(command.hubId())) {
            hubRouteEventPublisher.publishRouteBuildCompleted(command);
        }
    }

    /**
     * 중앙 허브에 대한 경로 생성
     */
    private Set<HubRoute> buildForCenter(BuildRouteCommand command) {
        Hub newCenterHub = getHubIncludingCreating(command.hubId());
        List<Hub> existingCenterHubs = hubRepository.findAllByHubType(HubType.CENTER)
                .stream()
                .filter(hub -> !hub.getHubId().equals(newCenterHub.getHubId()))  // 자기 자신 제외
                .collect(Collectors.toList());
        Map<RouteKey, HubRoute> existingRouteMap = getExistingRouteMap(newCenterHub);

        // Domain Service에 Route 생성 로직 위임
        Set<HubRoute> routes = hubRouteDomainService.buildRouteSkeletonsForNewCenterHub(
            command.hubId(),
            newCenterHub,
            existingCenterHubs
        );
        Set<HubRoute> filteredRoutes = filterMissingRoutes(routes, existingRouteMap);
        scheduleEventRecovery(filteredRoutes);

        Set<UUID> insertedRouteIds = hubRouteRepository.insertIgnore(filteredRoutes);
        return filterInsertedRoutes(filteredRoutes, insertedRouteIds);
    }

    /**
     * 지점 허브에 대한 경로 생성
     */
    private Set<HubRoute> buildForBranch(BuildRouteCommand command) {
        Hub branchHub = getHubIncludingCreating(command.hubId());
        Hub centerHub = getHub(command.centerHubId());
        Map<RouteKey, HubRoute> existingRouteMap = getExistingRouteMap(branchHub);

        Set<HubRoute> routes = hubRouteDomainService.buildRouteSkeletonsForNewBranchHub(
            command.hubId(),
            branchHub,
            centerHub
        );
        Set<HubRoute> filteredRoutes = filterMissingRoutes(routes, existingRouteMap);
        scheduleEventRecovery(filteredRoutes);

        Set<UUID> insertedRouteIds = hubRouteRepository.insertIgnore(filteredRoutes);
        return filterInsertedRoutes(filteredRoutes, insertedRouteIds);
    }

    private Map<RouteKey, HubRoute> getExistingRouteMap(Hub hub) {
        return hubRouteRepository.findByStartHubOrEndHub(hub, hub).stream()
                .collect(Collectors.toMap(
                        route -> routeKey(route.getStartHub(), route.getEndHub()),
                        route -> route,
                        (existing, ignored) -> existing,
                        HashMap::new
                ));
    }

    private Set<HubRoute> filterMissingRoutes(Set<HubRoute> routes, Map<RouteKey, HubRoute> existingRouteMap) {
        return routes.stream()
                .filter(route -> !existingRouteMap.containsKey(routeKey(route.getStartHub(), route.getEndHub())))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Hub getHub(UUID hubId) {
        return hubRepository.findById(hubId)
            .orElseThrow(() -> new BusinessException(ErrorCode.HUB_NOT_FOUND));
    }

    private Hub getHubIncludingCreating(UUID hubId) {
        return hubRepository.findByIdIncludingCreating(hubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HUB_NOT_FOUND));
    }

    private Hub getHubIncludingDeleted(UUID hubId) {
        return hubRepository.findByIdIncludingDeleted(hubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HUB_NOT_FOUND));
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

    @Transactional
    public Optional<RoutePairBuildTarget> claimRoutePairBuild(List<UUID> routeIds) {
        List<UUID> distinctRouteIds = routeIds.stream().distinct().toList();
        List<HubRoute> routes = hubRouteRepository.findAllByIdsWithHubsForUpdate(distinctRouteIds);
        if (routes.size() != distinctRouteIds.size()) {
            return Optional.empty();
        }
        LocalDateTime staleBefore = LocalDateTime.now()
                .minus(eventRecoveryDelay());
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
                        && (route.getNextRefreshAt() == null
                        || !route.getNextRefreshAt().isAfter(now))
                        && (route.getRefreshClaimedAt() == null
                        || route.getRefreshClaimedAt().isBefore(staleBefore))
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

    public List<UUID> claimRouteBuildTargets(int batchSize, Duration staleProcessingTimeout) {
        LocalDateTime now = LocalDateTime.now();
        return hubRouteRepository.claimPendingForBuild(
                batchSize,
                now,
                now.minus(staleProcessingTimeout)
        );
    }

    public List<UUID> findRouteBuildRecoveryTargets(int batchSize, Duration staleProcessingTimeout) {
        LocalDateTime now = LocalDateTime.now();
        return hubRouteRepository.findRecoveryTargetIds(
                batchSize,
                now,
                now.minus(staleProcessingTimeout)
        );
    }

    public List<UUID> findRoutePairIds(UUID routeId) {
        return hubRouteRepository.findRoutePairIds(routeId);
    }

    public List<UUID> findRouteRefreshTargets(int batchSize, Duration staleRefreshTimeout) {
        LocalDateTime now = LocalDateTime.now();
        return hubRouteRepository.findRefreshTargetIds(
                batchSize,
                now,
                now.minus(staleRefreshTimeout)
        );
    }

    public boolean hasActiveBuildWork() {
        return hubRouteRepository.hasActiveBuildWork();
    }

    public Optional<RouteBuildTarget> getRouteBuildTarget(UUID routeId) {
        return hubRouteRepository.findByIdWithHubs(routeId)
                .filter(route -> !route.isComplete())
                .map(route -> new RouteBuildTarget(
                        route.getRouteId(),
                        route.getBuildHubId(),
                        route.getStartHub().getHubId(),
                        route.getStartHub().getCoordinate(),
                        route.getEndHub().getHubId(),
                        route.getEndHub().getCoordinate(),
                        resolvePurpose(route.getStartHub(), route.getEndHub())
                ));
    }

    @Transactional
    public void completeRouteBuild(UUID routeId, RouteWeight routeWeight) {
        HubRoute route = hubRouteRepository.findByIdWithHubs(routeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HUB_ROUTE_NOT_FOUND));
        if (route.isComplete()) {
            return;
        }

        route.complete(routeWeight);
        hubRouteRepository.save(route);
        hubRouteEventPublisher.publishRouteCreatedEvent(List.of(HubRouteCreatedEvent.from(route.getBuildHubId(), route)));

        if (route.getBuildHubId() != null && !hubRouteRepository.hasIncompleteRoutes(route.getBuildHubId())) {
            hubRouteEventPublisher.publishRouteBuildCompleted(buildCompletedCommand(route.getBuildHubId()));
        }
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
    public void failRouteBuild(UUID routeId, String reason, int maxRetries, Duration retryBackoff) {
        HubRoute route = hubRouteRepository.findByIdWithHubs(routeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HUB_ROUTE_NOT_FOUND));
        if (route.isComplete()) {
            return;
        }

        boolean finalFailed = route.failOrRetry(
                reason,
                maxRetries,
                LocalDateTime.now().plus(retryBackoff)
        );
        hubRouteRepository.save(route);

        if (finalFailed && route.getBuildHubId() != null) {
            hubRouteEventPublisher.publishRouteBuildFailed(route.getBuildHubId(), reason);
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

    private Set<HubRoute> filterInsertedRoutes(Set<HubRoute> routes, Set<UUID> insertedRouteIds) {
        if (insertedRouteIds == null || insertedRouteIds.isEmpty()) {
            return Set.of();
        }
        return routes.stream()
                .filter(route -> insertedRouteIds.contains(route.getRouteId()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void scheduleEventRecovery(Set<HubRoute> routes) {
        LocalDateTime recoveryAt = LocalDateTime.now()
                .plus(eventRecoveryDelay());
        routes.forEach(route -> route.scheduleRecovery(recoveryAt));
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

    private List<HubRouteBuildRequestedEvent> toBuildRequestedEvents(UUID hubId, Set<HubRoute> routes) {
        Map<RoutePairKey, List<HubRoute>> routePairs = routes.stream()
                .collect(Collectors.groupingBy(route -> RoutePairKey.of(
                        route.getStartHub().getHubId(),
                        route.getEndHub().getHubId()
                )));

        List<HubRouteBuildRequestedEvent> events = new ArrayList<>();
        for (List<HubRoute> pair : routePairs.values()) {
            List<UUID> routeIds = pair.stream()
                    .map(HubRoute::getRouteId)
                    .sorted(Comparator.comparing(UUID::toString))
                    .toList();
            events.add(new HubRouteBuildRequestedEvent(hubId, routeIds));
        }
        return events;
    }

    private BuildRouteCommand buildCompletedCommand(UUID buildHubId) {
        Hub hub = getHubIncludingCreating(buildHubId);
        UUID centerHubId = hub.isBranchHub()
                ? hub.getCenterHubs().stream().findFirst().map(Hub::getHubId).orElse(null)
                : null;
        return new BuildRouteCommand(centerHubId, hub.getHubId(), hub.getName(), hub.getAddress(), hub.getHubType());
    }

    /**
     * 허브 삭제 시 해당 허브와 연결된 모든 경로 소프트 삭제
     */
    @Transactional
    public void deleteRoutesForHub(UUID hubId, Long deletedBy) {
        Hub hub = getHubIncludingDeleted(hubId);
        
        // 해당 Hub가 시작점이거나 끝점인 모든 경로 조회
        List<HubRoute> routes = hubRouteRepository.findByStartHubOrEndHub(hub, hub);
        
        if (routes.isEmpty()) {
            log.info("No routes found for hub: {}", hub.getName());
            return;
        }
        
        // 모든 경로 소프트 삭제
        routes.forEach(route -> route.markDeleted(deletedBy));
        hubRouteRepository.saveAll(routes);

        List<HubRouteDeletedEvent> createEventList = routes.stream()
                .map(HubRouteDeletedEvent::from)
                .collect(Collectors.toList());
        hubRouteEventPublisher.publishRouteDeletedEvent(createEventList);
    }

    public List<HubRouteRes> getALLRoute() {
        return hubRouteRepository.findAll()
                .stream()
                .map(HubRouteRes::from)
                .collect(Collectors.toList());
    }

    private RouteKey routeKey(Hub startHub, Hub endHub) {
        return new RouteKey(startHub.getHubId(), endHub.getHubId());
    }

    private record RouteKey(UUID startHubId, UUID endHubId) {
    }

    private record RoutePairKey(UUID firstHubId, UUID secondHubId) {
        private static RoutePairKey of(UUID first, UUID second) {
            return first.toString().compareTo(second.toString()) <= 0
                    ? new RoutePairKey(first, second)
                    : new RoutePairKey(second, first);
        }
    }
}
