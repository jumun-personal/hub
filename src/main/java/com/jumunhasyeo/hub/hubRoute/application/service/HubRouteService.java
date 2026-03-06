package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hubRoute.application.HubRouteEventPublisher;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.HubRouteRes;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDeletedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import com.jumunhasyeo.hub.hubRoute.domain.service.HubRouteDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * 새로운 Hub 생성 시 경로 자동 생성
     */
    @Transactional
    public void buildRoutesForNewHub(BuildRouteCommand command) {
        prepareRoutesForBuildJob(command.hubId());
    }

    @Transactional
    public int prepareRoutesForBuildJob(UUID hubId) {
        Hub hub = getHubIncludingCreating(hubId);
        Set<HubRoute> hubRoutes = new HashSet<>();
        if (hub.isCenterHub()) {
            hubRoutes.addAll(buildForCenter(hub));
        } else if (hub.isBranchHub()) {
            hubRoutes.addAll(buildForBranch(hub));
        }
        return countRoutePairs(hubRoutes);
    }

    public List<UUID> findRouteBuildRecoveryTargets(int batchSize, Duration staleProcessingTimeout) {
        LocalDateTime now = LocalDateTime.now();
        return hubRouteRepository.findRecoveryTargetIds(
                batchSize,
                now,
                now.minus(staleProcessingTimeout)
        );
    }

    public List<UUID> findRunningJobBuildTargets(int batchSize, Duration staleProcessingTimeout) {
        LocalDateTime now = LocalDateTime.now();
        return hubRouteRepository.findRunningJobBuildTargetIds(
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

    public int resetFailedBuildRoutes(UUID buildHubId) {
        return hubRouteRepository.resetFailedBuildRoutes(buildHubId);
    }

    private Set<HubRoute> buildForCenter(Hub newCenterHub) {
        List<Hub> existingCenterHubs = hubRepository.findAllByHubType(HubType.CENTER)
                .stream()
                .filter(hub -> !hub.getHubId().equals(newCenterHub.getHubId()))
                .collect(Collectors.toList());
        Map<RouteKey, HubRoute> existingRouteMap = getExistingRouteMap(newCenterHub);

        Set<HubRoute> routes = hubRouteDomainService.buildRouteSkeletonsForNewCenterHub(
                newCenterHub.getHubId(),
                newCenterHub,
                existingCenterHubs
        );
        Set<HubRoute> filteredRoutes = filterBuildableRoutes(newCenterHub, filterMissingRoutes(routes, existingRouteMap));
        Set<UUID> insertedRouteIds = hubRouteRepository.insertIgnore(filteredRoutes);
        return filterInsertedRoutes(filteredRoutes, insertedRouteIds);
    }

    private Set<HubRoute> buildForBranch(Hub branchHub) {
        Hub centerHub = branchHub.getCenterHubs()
                .stream()
                .filter(Hub::isActive)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_CONNECTED_TO_CENTER));
        Map<RouteKey, HubRoute> existingRouteMap = getExistingRouteMap(branchHub);

        Set<HubRoute> routes = hubRouteDomainService.buildRouteSkeletonsForNewBranchHub(
                branchHub.getHubId(),
                branchHub,
                centerHub
        );
        Set<HubRoute> filteredRoutes = filterBuildableRoutes(branchHub, filterMissingRoutes(routes, existingRouteMap));
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

    private Set<HubRoute> filterBuildableRoutes(Hub buildHub, Set<HubRoute> routes) {
        return routes.stream()
                .filter(route -> isBuildEndpoint(buildHub, route.getStartHub()))
                .filter(route -> isBuildEndpoint(buildHub, route.getEndHub()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private boolean isBuildEndpoint(Hub buildHub, Hub endpoint) {
        return endpoint.getHubId().equals(buildHub.getHubId()) || endpoint.isActive();
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

    private Set<HubRoute> filterInsertedRoutes(Set<HubRoute> routes, Set<UUID> insertedRouteIds) {
        if (insertedRouteIds == null || insertedRouteIds.isEmpty()) {
            return Set.of();
        }
        return routes.stream()
                .filter(route -> insertedRouteIds.contains(route.getRouteId()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * 허브 삭제 시 해당 허브와 연결된 모든 경로 소프트 삭제
     */
    @Transactional
    public void deleteRoutesForHub(UUID hubId, Long deletedBy) {
        int deletedCount = hubRouteRepository.bulkSoftDeleteByHubId(hubId, deletedBy);
        log.info("Hub routes soft deleted. hubId={}, count={}", hubId, deletedCount);
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

    private int countRoutePairs(Set<HubRoute> routes) {
        return (int) routes.stream()
                .map(route -> routePairKey(route.getStartHub(), route.getEndHub()))
                .distinct()
                .count();
    }

    private RoutePairKey routePairKey(Hub startHub, Hub endHub) {
        UUID startHubId = startHub.getHubId();
        UUID endHubId = endHub.getHubId();
        return startHubId.compareTo(endHubId) <= 0
                ? new RoutePairKey(startHubId, endHubId)
                : new RoutePairKey(endHubId, startHubId);
    }

    private record RouteKey(UUID startHubId, UUID endHubId) {
    }

    private record RoutePairKey(UUID firstHubId, UUID secondHubId) {
    }
}
