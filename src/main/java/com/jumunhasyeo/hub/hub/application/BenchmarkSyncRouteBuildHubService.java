package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.application.command.CreateHubCommand;
import com.jumunhasyeo.hub.hub.application.command.DeleteHubCommand;
import com.jumunhasyeo.hub.hub.application.command.UpdateHubCommand;
import com.jumunhasyeo.hub.hub.application.dto.response.HubRes;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubNameUpdatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubUpdatedEvent;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepositoryCustom;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hub.presentation.dto.HubSearchCondition;
import com.jumunhasyeo.hub.hubRoute.application.dto.ProviderHint;
import com.jumunhasyeo.hub.hubRoute.application.dto.RoutePurpose;
import com.jumunhasyeo.hub.hubRoute.application.dto.request.RouteWeightQuery;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteBuildJobService;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderResolution;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import com.jumunhasyeo.hub.hubRoute.domain.service.HubRouteDomainService;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class BenchmarkSyncRouteBuildHubService implements HubService {
    private final HubRepository hubRepository;
    private final HubRepositoryCustom hubRepositoryCustom;
    private final HubEventPublisher hubEventPublisher;
    private final HubRouteService hubRouteService;
    private final HubRouteBuildJobService hubRouteBuildJobService;
    private final HubRouteRepository hubRouteRepository;
    private final HubRouteDomainService hubRouteDomainService;
    private final RouteProviderResolution routeProviderResolution;

    @Transactional
    public HubRes create(CreateHubCommand command) {
        Coordinate coordinate = Coordinate.of(command.latitude(), command.longitude());
        Address address = Address.of(command.address(), coordinate);
        Hub hub = Hub.of(command.name(), address, command.hubType());

        switch (hub.getHubType()) {
            case BRANCH -> createBranchHub(command.centerHubId(), hub);
            case CENTER -> createCenterHub(hub);
            default -> throw new BusinessException(ErrorCode.INVALID_HUB_TYPE);
        }
        return HubRes.from(hub);
    }

    @Transactional
    public HubRes update(UpdateHubCommand command) {
        Hub hub = getHub(command.hubId());
        String preName = hub.getName();
        Coordinate coordinate = Coordinate.of(command.latitude(), command.longitude());
        Address address = Address.of(command.address(), coordinate);
        hub.update(command.name(), address);

        if (isChangedName(preName, hub)) {
            hubEventPublisher.publishEvent(HubNameUpdatedEvent.of(hub));
        }
        hubEventPublisher.publishEvent(HubUpdatedEvent.of(hub));
        return HubRes.from(hub);
    }

    @Transactional
    public UUID delete(DeleteHubCommand command) {
        Hub hub = getHub(command.hubId());
        hub.delete(command.userId());
        hubRouteService.deleteRoutesForHub(hub.getHubId(), command.userId());
        hubRouteBuildJobService.cancel(hub.getHubId(), "hub deleted");
        hubEventPublisher.publishEvent(HubDeletedEvent.from(hub, command.userId()));
        return hub.getHubId();
    }

    @Override
    public Boolean existById(UUID uuid) {
        return hubRepository.existById(uuid);
    }

    @Override
    public List<HubRes> getAll() {
        return hubRepository.findAll()
                .stream()
                .map(HubRes::from)
                .collect(Collectors.toList());
    }

    public HubRes getById(UUID hubId) {
        return HubRes.from(getHub(hubId));
    }

    public Page<HubRes> search(HubSearchCondition condition, Pageable pageable) {
        return hubRepositoryCustom.searchHubsByCondition(condition, pageable);
    }

    private void createCenterHub(Hub hub) {
        hubRepository.save(hub);
        buildRoutesSynchronously(hub);
        hub.activate();
        hubEventPublisher.publishEvent(HubCreatedEvent.centerHub(hub));
    }

    private void createBranchHub(UUID centerHubId, Hub hub) {
        Hub centerHub = getHub(centerHubId);
        hub.addCenterHub(centerHub);
        hubRepository.save(hub);
        buildRoutesSynchronously(hub);
        hub.activate();
        hubEventPublisher.publishEvent(HubCreatedEvent.branchHub(hub, centerHub.getHubId()));
    }

    private void buildRoutesSynchronously(Hub hub) {
        Set<HubRoute> routes = hub.isCenterHub()
                ? buildForCenter(hub)
                : buildForBranch(hub);
        Set<HubRoute> missingRoutes = filterMissingRoutes(routes, getExistingRouteMap(hub));
        Set<UUID> insertedRouteIds = hubRouteRepository.insertIgnore(missingRoutes);
        log.info("Benchmark sync route build completed. hubId={}, generatedRows={}, insertedRows={}",
                hub.getHubId(), routes.size(), insertedRouteIds.size());
    }

    private Set<HubRoute> buildForCenter(Hub newCenterHub) {
        List<Hub> existingCenterHubs = hubRepository.findAllByHubType(HubType.CENTER)
                .stream()
                .filter(hub -> !hub.getHubId().equals(newCenterHub.getHubId()))
                .collect(Collectors.toList());
        return hubRouteDomainService.buildRoutesForNewCenterHub(
                newCenterHub,
                existingCenterHubs,
                this::calculateWeight
        );
    }

    private Set<HubRoute> buildForBranch(Hub branchHub) {
        Hub centerHub = branchHub.getCenterHubs()
                .stream()
                .filter(Hub::isActive)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_CONNECTED_TO_CENTER));
        return hubRouteDomainService.buildRoutesForNewBranchHub(
                branchHub,
                centerHub,
                this::calculateWeight
        );
    }

    private RouteWeight calculateWeight(Hub from, Hub to) {
        RouteWeightResult result = routeProviderResolution.resolve(new RouteWeightQuery(
                from.getHubId(),
                from.getCoordinate(),
                to.getCoordinate(),
                routePurpose(from, to),
                ProviderHint.PRIMARY
        ));
        return RouteWeight.of(result.distanceKm(), result.durationMinutes());
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

    private RoutePurpose routePurpose(Hub from, Hub to) {
        if (from.isCenterHub() && to.isCenterHub()) {
            return RoutePurpose.CENTER_TO_CENTER;
        }
        if (from.isBranchHub() && to.isBranchHub()) {
            return RoutePurpose.BRANCH_TO_BRANCH;
        }
        return RoutePurpose.BRANCH_TO_CENTER;
    }

    private Hub getHub(UUID hubId) {
        return hubRepository.findById(hubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HUB_NOT_FOUND));
    }

    private RouteKey routeKey(Hub startHub, Hub endHub) {
        return new RouteKey(startHub.getHubId(), endHub.getHubId());
    }

    private boolean isChangedName(String preName, Hub hub) {
        return !preName.equals(hub.getName());
    }

    private record RouteKey(UUID startHubId, UUID endHubId) {
    }
}
