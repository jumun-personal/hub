package com.jumunhasyeo.hub.hubRoute.domain.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface HubRouteRepository {
    void save(HubRoute forwardRoute);
    void saveAll(List<HubRoute> routes);
    Set<UUID> insertIgnore(Set<HubRoute> createAllRoute);
    List<HubRoute> findAll();
    List<HubRoute> findByStartHubOrEndHub(Hub startHub, Hub endHub);
    List<UUID> claimPendingForBuild(int limit, LocalDateTime now, LocalDateTime staleBefore);
    List<UUID> findRecoveryTargetIds(int limit, LocalDateTime now, LocalDateTime staleBefore);
    List<UUID> findRefreshTargetIds(int limit, LocalDateTime now, LocalDateTime staleBefore);
    List<UUID> findRoutePairIds(UUID routeId);
    Optional<HubRoute> findByIdWithHubs(UUID routeId);
    List<HubRoute> findAllByIdsWithHubsForUpdate(List<UUID> routeIds);
    void lockBuildHub(UUID buildHubId);
    boolean hasActiveBuildWork();
    boolean hasIncompleteRoutes(UUID buildHubId);
}
