package com.jumunhasyeo.hub.hubRoute.infrastructure.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;

public interface JpaHubRouteRepositoryImpl extends JpaRepository<HubRoute, UUID> {

    @Query("SELECT hr FROM HubRoute hr WHERE (hr.startHub = :startHub OR hr.endHub = :endHub) AND hr.isDeleted = false")
    List<HubRoute> findByStartHubOrEndHub(
            @Param("startHub") Hub startHub, 
            @Param("endHub") Hub endHub
    );
    
    @Query("""
            SELECT hr
            FROM HubRoute hr
            WHERE hr.isDeleted = false
              AND (hr.status = com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus.COMPLETE OR hr.status IS NULL)
            """)
    List<HubRoute> findAll();

    @Modifying
    @Query(value = """
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
                    created_at,
                    modified_at,
                    deleted_at,
                    is_deleted
                )
                VALUES (
                    gen_random_uuid(),
                    :startId,
                    :endId,
                    :buildHubId,
                    :status,
                    :retryCount,
                    :nextRetryAt,
                    :errorMessage,
                    :distanceKm,
                    :durationMinutes,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    NULL,
                    false
                )
                ON CONFLICT (start_hub_id, end_hub_id, is_deleted) 
                DO NOTHING;
            """, nativeQuery = true)
    void insertIgnore(
                       @Param("startId") UUID startId,
                       @Param("endId") UUID endId,
                       @Param("buildHubId") UUID buildHubId,
                       @Param("status") String status,
                       @Param("retryCount") int retryCount,
                       @Param("nextRetryAt") java.time.LocalDateTime nextRetryAt,
                       @Param("errorMessage") String errorMessage,
                       @Param("distanceKm") Double distanceKm,
                       @Param("durationMinutes") Integer durationMinutes
    );

    default void insertIgnore(UUID startId, UUID endId, double distanceKm, int durationMinutes) {
        insertIgnore(
                startId,
                endId,
                null,
                HubRouteStatus.COMPLETE.name(),
                0,
                null,
                null,
                distanceKm,
                durationMinutes
        );
    }

    @Query(value = """
            SELECT route_id
            FROM p_hub_route
            WHERE is_deleted = false
              AND (
                    (
                        route_status = 'PENDING'
                        AND next_retry_at IS NOT NULL
                        AND next_retry_at <= :now
                    )
                    OR (
                        route_status = 'PROCESSING'
                        AND modified_at < :staleBefore
                    )
              )
            ORDER BY COALESCE(next_retry_at, modified_at), created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findRecoveryTargetIds(
            @Param("limit") int limit,
            @Param("now") java.time.LocalDateTime now,
            @Param("staleBefore") java.time.LocalDateTime staleBefore
    );

    @Query(value = """
            SELECT route_id
            FROM p_hub_route
            WHERE is_deleted = false
              AND route_status = 'COMPLETE'
              AND (next_refresh_at IS NULL OR next_refresh_at <= :now)
              AND (refresh_claimed_at IS NULL OR refresh_claimed_at < :staleBefore)
            ORDER BY next_refresh_at NULLS FIRST, created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findRefreshTargetIds(
            @Param("limit") int limit,
            @Param("now") java.time.LocalDateTime now,
            @Param("staleBefore") java.time.LocalDateTime staleBefore
    );

    @Query(value = """
            SELECT pair.route_id
            FROM p_hub_route seed
            JOIN p_hub_route pair
              ON pair.build_hub_id IS NOT DISTINCT FROM seed.build_hub_id
             AND pair.is_deleted = false
             AND (
                    (pair.start_hub_id = seed.start_hub_id AND pair.end_hub_id = seed.end_hub_id)
                 OR (pair.start_hub_id = seed.end_hub_id AND pair.end_hub_id = seed.start_hub_id)
             )
            WHERE seed.route_id = :routeId
            ORDER BY pair.route_id
            """, nativeQuery = true)
    List<UUID> findRoutePairIds(@Param("routeId") UUID routeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT route
            FROM HubRoute route
            JOIN FETCH route.startHub
            JOIN FETCH route.endHub
            WHERE route.routeId IN :routeIds
            ORDER BY route.routeId
            """)
    List<HubRoute> findAllByIdsWithHubsForUpdate(@Param("routeIds") List<UUID> routeIds);

    @Query("""
            SELECT COUNT(route) > 0
            FROM HubRoute route
            WHERE route.isDeleted = false
              AND route.status IN (
                    com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus.PENDING,
                    com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus.PROCESSING
              )
            """)
    boolean existsActiveBuildWork();

    @Query("""
            SELECT COUNT(route) > 0
            FROM HubRoute route
            WHERE route.buildHubId = :buildHubId
              AND route.isDeleted = false
              AND route.status <> com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus.COMPLETE
            """)
    boolean existsIncompleteByBuildHubId(@Param("buildHubId") UUID buildHubId);
}
