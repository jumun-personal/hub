package com.jumunhasyeo.hub.hubRoute.domain.entity;

import com.jumunhasyeo.common.BaseEntity;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.UUID;

@Entity
@Table(
        name = "p_hub_route",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_start_end_hub_deleted_at",
                columnNames = {"start_hub_id", "end_hub_id", "is_deleted"}
        ),
        indexes = {
                @Index(name = "idx_hub_route_build_status_retry", columnList = "build_hub_id, route_status, next_retry_at"),
                @Index(name = "idx_hub_route_status_modified_at", columnList = "route_status, modified_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class HubRoute extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "route_id")
    private UUID routeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "start_hub_id", nullable = false)
    private Hub startHub;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "end_hub_id", nullable = false)
    private Hub endHub;

    @Column(name = "build_hub_id")
    private UUID buildHubId;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_status", nullable = false)
    @Builder.Default
    private HubRouteStatus status = HubRouteStatus.PENDING;

    @Embedded
    private RouteWeight routeWeight;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public static HubRoute of(Hub startHub, Hub endHub, RouteWeight weight) {
        return HubRoute.builder()
                .startHub(startHub)
                .endHub(endHub)
                .routeWeight(weight)
                .status(HubRouteStatus.COMPLETE)
                .build();
    }

    public static HubRoute ofSelfId(UUID routeId, Hub startHub, Hub endHub, RouteWeight weight) {
        return HubRoute.builder()
                .routeId(routeId)
                .startHub(startHub)
                .endHub(endHub)
                .routeWeight(weight)
                .status(HubRouteStatus.COMPLETE)
                .build();
    }

    public static HubRoute skeleton(UUID buildHubId, Hub startHub, Hub endHub) {
        return HubRoute.builder()
                .routeId(UUID.randomUUID())
                .buildHubId(buildHubId)
                .startHub(startHub)
                .endHub(endHub)
                .status(HubRouteStatus.PENDING)
                .retryCount(0)
                .build();
    }

    public static HashSet<HubRoute> createTwoWay(Hub from, Hub to, RouteWeight routeWeight) {
        HashSet<HubRoute> hubRoutes = new HashSet<>();
        hubRoutes.add(ofSelfId(UUID.randomUUID(), from, to, routeWeight));
        hubRoutes.add(ofSelfId(UUID.randomUUID(), to, from, routeWeight));
        return hubRoutes;
    }

    public static HashSet<HubRoute> createTwoWaySkeleton(UUID buildHubId, Hub from, Hub to) {
        HashSet<HubRoute> hubRoutes = new HashSet<>();
        hubRoutes.add(skeleton(buildHubId, from, to));
        hubRoutes.add(skeleton(buildHubId, to, from));
        return hubRoutes;
    }

    public void claimProcessing() {
        this.status = HubRouteStatus.PROCESSING;
        this.nextRetryAt = null;
        this.errorMessage = null;
    }

    public void complete(RouteWeight routeWeight) {
        this.routeWeight = routeWeight;
        this.status = HubRouteStatus.COMPLETE;
        this.nextRetryAt = null;
        this.errorMessage = null;
    }

    public boolean failOrRetry(String errorMessage, int maxRetries, LocalDateTime nextRetryAt) {
        this.retryCount = this.retryCount + 1;
        this.errorMessage = errorMessage;
        if (this.retryCount >= maxRetries) {
            this.status = HubRouteStatus.FAILED;
            this.nextRetryAt = null;
            return true;
        }
        this.status = HubRouteStatus.PENDING;
        this.nextRetryAt = nextRetryAt;
        return false;
    }

    public boolean isComplete() {
        return HubRouteStatus.COMPLETE.equals(status);
    }
}
