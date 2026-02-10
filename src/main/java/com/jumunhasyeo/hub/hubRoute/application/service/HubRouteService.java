package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hubRoute.application.HubRouteEventPublisher;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;
import com.jumunhasyeo.hub.hubRoute.application.dto.ProviderHint;
import com.jumunhasyeo.hub.hubRoute.application.dto.RoutePurpose;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.HubRouteRes;
import com.jumunhasyeo.hub.hubRoute.application.dto.request.RouteWeightQuery;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteCreatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDeletedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import com.jumunhasyeo.hub.hubRoute.domain.service.HubRouteDomainService;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final RouteWeightApiService routeWeightApi;
    private final HubRouteRepository hubRouteRepository;
    private final HubRouteDomainService hubRouteDomainService;
    private final HubRouteEventPublisher hubRouteEventPublisher;

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
            List<HubRouteCreatedEvent> createEventList = hubRoutes.stream()
                    .map(route -> HubRouteCreatedEvent.from(command.hubId(), route))
                    .collect(Collectors.toList());
            hubRouteEventPublisher.publishRouteCreatedEvent(createEventList);
        }
        hubRouteEventPublisher.publishRouteBuildCompleted(command);
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
        Set<HubRoute> routes = hubRouteDomainService.buildRoutesForNewCenterHub(
            newCenterHub, 
            existingCenterHubs,
            (from, to) -> resolveRouteWeight(from, to, existingRouteMap)
        );
        Set<HubRoute> filteredRoutes = filterMissingRoutes(routes, existingRouteMap);

        hubRouteRepository.insertIgnore(filteredRoutes);
        return filteredRoutes;
    }

    /**
     * 지점 허브에 대한 경로 생성
     */
    private Set<HubRoute> buildForBranch(BuildRouteCommand command) {
        Hub branchHub = getHubIncludingCreating(command.hubId());
        Hub centerHub = getHub(command.centerHubId());
        Map<RouteKey, HubRoute> existingRouteMap = getExistingRouteMap(branchHub);

        Set<HubRoute> routes = hubRouteDomainService.buildRoutesForNewBranchHub(
            branchHub,
            centerHub,
            (from, to) -> resolveRouteWeight(from, to, existingRouteMap)
        );
        Set<HubRoute> filteredRoutes = filterMissingRoutes(routes, existingRouteMap);

        hubRouteRepository.insertIgnore(filteredRoutes);
        return filteredRoutes;
    }

    /**
     * 경로 가중치(시간,거리) 계산
     */
    private RouteWeight calculateRouteWeight(Hub from, Hub to) {
        RouteWeightQuery query = new RouteWeightQuery(
                from.getHubId(),
                from.getCoordinate(),
                to.getCoordinate(),
                resolvePurpose(from, to),
                ProviderHint.ANY
        );
        RouteWeightResult response = routeWeightApi.getRouteInfo(query);
        return RouteWeight.of(response.distanceKm(), response.durationMinutes());
    }

    private RouteWeight resolveRouteWeight(Hub from, Hub to, Map<RouteKey, HubRoute> existingRouteMap) {
        HubRoute existing = existingRouteMap.get(routeKey(from, to));
        if (existing != null) {
            return existing.getRouteWeight();
        }

        HubRoute reverse = existingRouteMap.get(routeKey(to, from));
        if (reverse != null) {
            return reverse.getRouteWeight();
        }

        return calculateRouteWeight(from, to);
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
        if (from.isBranchHub() && to.isCenterHub()) {
            return RoutePurpose.BRANCH_TO_CENTER;
        }
        return RoutePurpose.BRANCH_TO_BRANCH;
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
}
