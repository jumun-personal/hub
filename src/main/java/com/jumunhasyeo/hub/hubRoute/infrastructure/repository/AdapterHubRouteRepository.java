package com.jumunhasyeo.hub.hubRoute.infrastructure.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AdapterHubRouteRepository implements HubRouteRepository {
    private static final String INSERT_IGNORE_SQL = """
            INSERT INTO p_hub_route(
                route_id,
                start_hub_id,
                end_hub_id,
                build_hub_id,
                route_status,
                retry_count,
                next_retry_at,
                error_message,
                distance_km,
                duration_minutes,
                next_refresh_at,
                refresh_claimed_at,
                created_at,
                modified_at,
                deleted_at,
                is_deleted
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, false)
            ON CONFLICT (start_hub_id, end_hub_id, is_deleted)
            DO NOTHING
            """;

    private final JpaHubRouteRepositoryImpl repository;
    private final JdbcTemplate jdbcTemplate;

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
    public Set<UUID> insertIgnore(Set<HubRoute> createAllRoute) {
        if (createAllRoute.isEmpty()) {
            return Set.of();
        }

        List<HubRoute> routes = new ArrayList<>(createAllRoute);
        jdbcTemplate.batchUpdate(
                INSERT_IGNORE_SQL,
                routes,
                routes.size(),
                (statement, hubRoute) -> {
                    RouteWeight routeWeight = hubRoute.getRouteWeight();
                    statement.setObject(1, hubRoute.getRouteId());
                    statement.setObject(2, hubRoute.getStartHub().getHubId());
                    statement.setObject(3, hubRoute.getEndHub().getHubId());
                    statement.setObject(4, hubRoute.getBuildHubId());
                    statement.setString(5, hubRoute.getStatus().name());
                    statement.setInt(6, hubRoute.getRetryCount());
                    if (hubRoute.getNextRetryAt() == null) {
                        statement.setNull(7, Types.TIMESTAMP);
                    } else {
                        statement.setTimestamp(7, Timestamp.valueOf(hubRoute.getNextRetryAt()));
                    }
                    statement.setString(8, hubRoute.getErrorMessage());
                    if (routeWeight == null) {
                        statement.setNull(9, Types.NUMERIC);
                        statement.setNull(10, Types.INTEGER);
                    } else {
                        statement.setBigDecimal(9, routeWeight.getDistanceKm());
                        statement.setInt(10, routeWeight.getDurationMinutes());
                    }
                    if (hubRoute.getNextRefreshAt() == null) {
                        statement.setNull(11, Types.TIMESTAMP);
                    } else {
                        statement.setTimestamp(11, Timestamp.valueOf(hubRoute.getNextRefreshAt()));
                    }
                    if (hubRoute.getRefreshClaimedAt() == null) {
                        statement.setNull(12, Types.TIMESTAMP);
                    } else {
                        statement.setTimestamp(12, Timestamp.valueOf(hubRoute.getRefreshClaimedAt()));
                    }
                }
        );

        String placeholders = String.join(",", Collections.nCopies(routes.size(), "?"));
        List<UUID> insertedRouteIds = jdbcTemplate.query(
                "SELECT route_id FROM p_hub_route WHERE route_id IN (" + placeholders + ")",
                (resultSet, rowNum) -> resultSet.getObject("route_id", UUID.class),
                routes.stream().map(HubRoute::getRouteId).toArray()
        );
        return Set.copyOf(insertedRouteIds);
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
    public List<UUID> findRecoveryTargetIds(int limit, LocalDateTime now, LocalDateTime staleBefore) {
        return repository.findRecoveryTargetIds(limit, now, staleBefore);
    }

    @Override
    public List<UUID> findRefreshTargetIds(int limit, LocalDateTime now, LocalDateTime staleBefore) {
        return repository.findRefreshTargetIds(limit, now, staleBefore);
    }

    @Override
    public List<UUID> findRoutePairIds(UUID routeId) {
        return repository.findRoutePairIds(routeId);
    }

    @Override
    public Optional<HubRoute> findByIdWithHubs(UUID routeId) {
        return repository.findByIdWithHubs(routeId);
    }

    @Override
    public List<HubRoute> findAllByIdsWithHubsForUpdate(List<UUID> routeIds) {
        return repository.findAllByIdsWithHubsForUpdate(routeIds);
    }

    @Override
    public void lockBuildHub(UUID buildHubId) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), 0))"
            )) {
                statement.setObject(1, buildHubId);
                statement.execute();
                return null;
            }
        });
    }

    @Override
    public boolean hasActiveBuildWork() {
        return repository.existsActiveBuildWork();
    }

    @Override
    public boolean hasIncompleteRoutes(UUID buildHubId) {
        return repository.existsIncompleteByBuildHubId(buildHubId);
    }
}
