package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hubRoute.application.HubRouteEventPublisher;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;
import com.jumunhasyeo.hub.hubRoute.application.command.RouteBuildTarget;
import com.jumunhasyeo.hub.hubRoute.application.dto.RoutePurpose;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.HubRouteRes;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildRequestedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteCreatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDeletedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import com.jumunhasyeo.hub.hubRoute.domain.service.HubRouteDomainService;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

    private Set<HubRoute> buildForCenter(BuildRouteCommand command) {
        Hub newCenterHub = getHubIncludingCreating(command.hubId());
        List<Hub> existingCenterHubs = hubRepository.findAllByHubType(HubType.CENTER)
                .stream()
                .filter(hub -> !hub.getHubId().equals(newCenterHub.getHubId()))
                .collect(Collectors.toList());
        Map<RouteKey, HubRoute> existingRouteMap = getExistingRouteMap(newCenterHub);

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

    private Set<HubRoute> filterInsertedRoutes(Set<HubRoute> routes, Set<UUID> insertedRouteIds) {
        if (insertedRouteIds == null || insertedRouteIds.isEmpty()) {
            return Set.of();
        }
        return routes.stream()
                .filter(route -> insertedRouteIds.contains(route.getRouteId()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void scheduleEventRecovery(Set<HubRoute> routes) {
        LocalDateTime recoveryAt = LocalDateTime.now().plus(eventRecoveryDelay());
        routes.forEach(route -> route.scheduleRecovery(recoveryAt));
    }

    private Duration eventRecoveryDelay() {
        return DurationStyle.detectAndParse(
                eventRecoveryDelay == null || eventRecoveryDelay.isBlank() ? "5m" : eventRecoveryDelay
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
        List<HubRoute> routes = hubRouteRepository.findByStartHubOrEndHub(hub, hub);

        if (routes.isEmpty()) {
            log.info("No routes found for hub: {}", hub.getName());
            return;
        }

        routes.forEach(route -> route.markDeleted(deletedBy));
        hubRouteRepository.saveAll(routes);

        List<HubRouteDeletedEvent> deleteEvents = routes.stream()
                .map(HubRouteDeletedEvent::from)
                .collect(Collectors.toList());
        hubRouteEventPublisher.publishRouteDeletedEvent(deleteEvents);
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
