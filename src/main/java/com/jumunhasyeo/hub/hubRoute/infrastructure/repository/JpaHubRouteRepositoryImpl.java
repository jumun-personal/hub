package com.jumunhasyeo.hub.hubRoute.infrastructure.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                        AND (next_retry_at IS NULL OR next_retry_at <= :now)
                    )
                    OR (
                        route_status = 'PROCESSING'
                        AND modified_at < :staleBefore
                    )
              )
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> findBuildTargetIdsForUpdateSkipLocked(
            @Param("limit") int limit,
            @Param("now") java.time.LocalDateTime now,
            @Param("staleBefore") java.time.LocalDateTime staleBefore
    );

    @Modifying
    @Query("""
            UPDATE HubRoute route
            SET route.status = :status,
                route.nextRetryAt = NULL,
                route.errorMessage = NULL,
                route.modifiedAt = :claimedAt
            WHERE route.routeId IN :routeIds
            """)
    int markProcessing(
            @Param("routeIds") List<UUID> routeIds,
            @Param("status") HubRouteStatus status,
            @Param("claimedAt") java.time.LocalDateTime claimedAt
    );

    @Query("""
            SELECT route
            FROM HubRoute route
            JOIN FETCH route.startHub
            JOIN FETCH route.endHub
            WHERE route.routeId = :routeId
            """)
    Optional<HubRoute> findByIdWithHubs(@Param("routeId") UUID routeId);

    @Query("""
            SELECT COUNT(route) > 0
            FROM HubRoute route
            WHERE route.buildHubId = :buildHubId
              AND route.isDeleted = false
              AND route.status <> com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus.COMPLETE
            """)
    boolean existsIncompleteByBuildHubId(@Param("buildHubId") UUID buildHubId);
}
