package com.jumunhasyeo.hub.hubRoute.domain.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface HubRouteRepository {
    void saveAll(List<HubRoute> routes);
    Set<UUID> insertIgnore(Set<HubRoute> createAllRoute);
    List<HubRoute> findAll();
    List<HubRoute> findByStartHubOrEndHub(Hub startHub, Hub endHub);
    List<UUID> findRecoveryTargetIds(int limit, LocalDateTime now, LocalDateTime staleBefore);
    List<UUID> findRunningJobBuildTargetIds(int limit, LocalDateTime now, LocalDateTime staleBefore);
    List<UUID> findRefreshTargetIds(int limit, LocalDateTime now, LocalDateTime staleBefore);
    List<UUID> findRoutePairIds(UUID routeId);
    List<HubRoute> findAllByIdsWithHubsForUpdate(List<UUID> routeIds);
    boolean hasActiveBuildWork();
    boolean hasIncompleteRoutes(UUID buildHubId);
    boolean hasActiveBuildRoutes(UUID buildHubId);
    boolean hasFailedBuildRoutes(UUID buildHubId);
    int resetFailedBuildRoutes(UUID buildHubId);
    int bulkSoftDeleteByHubId(UUID hubId, Long deletedBy);
}
