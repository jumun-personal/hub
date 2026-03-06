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
                @Index(name = "idx_hub_route_refresh_due", columnList = "route_status, next_refresh_at, refresh_claimed_at"),
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

    @Enumerated(EnumType.STRING)
    @Column(name = "resolved_provider")
    private RouteProvider resolvedProvider;

    @Column(name = "resolved_by_fallback")
    private Boolean resolvedByFallback;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "processing_token")
    private UUID processingToken;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "next_refresh_at")
    private LocalDateTime nextRefreshAt;

    @Column(name = "refresh_claimed_at")
    private LocalDateTime refreshClaimedAt;

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
                .nextRetryAt(LocalDateTime.now())
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

    public void claimProcessing(UUID processingToken) {
        this.status = HubRouteStatus.PROCESSING;
        this.processingToken = processingToken;
        this.nextRetryAt = null;
        this.errorMessage = null;
    }

    public void claimProcessing() {
        claimProcessing(UUID.randomUUID());
    }

    public boolean isClaimedBy(UUID processingToken) {
        return HubRouteStatus.PROCESSING.equals(this.status)
                && this.processingToken != null
                && this.processingToken.equals(processingToken);
    }

    public void scheduleRecovery(LocalDateTime recoveryAt) {
        this.nextRetryAt = recoveryAt;
    }

    public void defer(LocalDateTime nextAttemptAt, String reason) {
        this.status = HubRouteStatus.PENDING;
        this.processingToken = null;
        this.nextRetryAt = nextAttemptAt;
        this.errorMessage = reason;
    }

    public void complete(RouteWeight routeWeight) {
        complete(routeWeight, RouteProvider.UNKNOWN, false);
    }

    public void complete(RouteWeight routeWeight, RouteProvider provider, boolean fallback) {
        this.routeWeight = routeWeight;
        this.resolvedProvider = provider;
        this.resolvedByFallback = fallback;
        this.status = HubRouteStatus.COMPLETE;
        this.processingToken = null;
        this.nextRetryAt = null;
        this.errorMessage = null;
    }

    public void complete(RouteWeight routeWeight, LocalDateTime nextRefreshAt) {
        complete(routeWeight, RouteProvider.UNKNOWN, false, nextRefreshAt);
    }

    public void complete(
            RouteWeight routeWeight,
            RouteProvider provider,
            boolean fallback,
            LocalDateTime nextRefreshAt
    ) {
        complete(routeWeight, provider, fallback);
        this.nextRefreshAt = nextRefreshAt;
        this.refreshClaimedAt = null;
    }

    public void claimRefresh(LocalDateTime claimedAt) {
        this.refreshClaimedAt = claimedAt;
    }

    public void completeRefresh(RouteWeight routeWeight, LocalDateTime nextRefreshAt) {
        completeRefresh(routeWeight, RouteProvider.UNKNOWN, false, nextRefreshAt);
    }

    public void completeRefresh(
            RouteWeight routeWeight,
            RouteProvider provider,
            boolean fallback,
            LocalDateTime nextRefreshAt
    ) {
        this.routeWeight = routeWeight;
        this.resolvedProvider = provider;
        this.resolvedByFallback = fallback;
        this.nextRefreshAt = nextRefreshAt;
        this.refreshClaimedAt = null;
        this.errorMessage = null;
    }

    public void deferRefresh(LocalDateTime nextAttemptAt, String reason) {
        this.nextRefreshAt = nextAttemptAt;
        this.refreshClaimedAt = null;
        this.errorMessage = reason;
    }

    public boolean failOrRetry(String errorMessage, int maxRetries, LocalDateTime nextRetryAt) {
        this.retryCount = this.retryCount + 1;
        this.errorMessage = errorMessage;
        if (this.retryCount >= maxRetries) {
            this.status = HubRouteStatus.FAILED;
            this.processingToken = null;
            this.nextRetryAt = null;
            return true;
        }
        this.status = HubRouteStatus.PENDING;
        this.processingToken = null;
        this.nextRetryAt = nextRetryAt;
        return false;
    }

    public void failPermanently(String errorMessage) {
        this.retryCount = this.retryCount + 1;
        this.errorMessage = errorMessage;
        this.status = HubRouteStatus.FAILED;
        this.processingToken = null;
        this.nextRetryAt = null;
    }

    public boolean isComplete() {
        return HubRouteStatus.COMPLETE.equals(status);
    }
}
