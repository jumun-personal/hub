package com.jumunhasyeo.hub.hubRoute.infrastructure.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AdapterHubRouteRepository implements HubRouteRepository {
    private final JpaHubRouteRepositoryImpl repository;

    @Override
    public void save(HubRoute forwardRoute) {
        repository.save(forwardRoute);
    }

    @Override
    public void saveAll(List<HubRoute> routes) {
        repository.saveAll(routes);
    }

    @Override
    @Transactional
    public void insertIgnore(Set<HubRoute> createAllRoute) {
        for (HubRoute hubRoute : createAllRoute) {
            RouteWeight routeWeight = hubRoute.getRouteWeight();
            repository.insertIgnore(
                    hubRoute.getStartHub().getHubId(),
                    hubRoute.getEndHub().getHubId(),
                    hubRoute.getBuildHubId(),
                    hubRoute.getStatus().name(),
                    hubRoute.getRetryCount(),
                    hubRoute.getNextRetryAt(),
                    hubRoute.getErrorMessage(),
                    routeWeight == null ? null : routeWeight.getDistanceKm().doubleValue(),
                    routeWeight == null ? null : routeWeight.getDurationMinutes()
            );
        }
    }

    @Override
    public List<HubRoute> findAll() {
        return repository.findAll();
    }

    @Override
    public List<HubRoute> findByStartHubOrEndHub(Hub startHub, Hub endHub) {
        return repository.findByStartHubOrEndHub(startHub, endHub);
    }

    @Override
    @Transactional
    public List<UUID> claimPendingForBuild(int limit, LocalDateTime now, LocalDateTime staleBefore) {
        List<UUID> routeIds = repository.findBuildTargetIdsForUpdateSkipLocked(limit, now, staleBefore);
        if (routeIds.isEmpty()) {
            return routeIds;
        }
        repository.markProcessing(routeIds, HubRouteStatus.PROCESSING, now);
        return routeIds;
    }

    @Override
    public Optional<HubRoute> findByIdWithHubs(UUID routeId) {
        return repository.findByIdWithHubs(routeId);
    }

    @Override
    public boolean hasIncompleteRoutes(UUID buildHubId) {
        return repository.existsIncompleteByBuildHubId(buildHubId);
    }
}
