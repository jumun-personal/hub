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
    void insertIgnore(Set<HubRoute> createAllRoute);
    List<HubRoute> findAll();
    List<HubRoute> findByStartHubOrEndHub(Hub startHub, Hub endHub);
    List<UUID> claimPendingForBuild(int limit, LocalDateTime now, LocalDateTime staleBefore);
    Optional<HubRoute> findByIdWithHubs(UUID routeId);
    boolean hasIncompleteRoutes(UUID buildHubId);
}
