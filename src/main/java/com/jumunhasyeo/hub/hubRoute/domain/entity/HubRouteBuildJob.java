package com.jumunhasyeo.hub.hubRoute.domain.entity;

import com.jumunhasyeo.common.BaseEntity;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

@Entity
@Table(name = "p_hub_route_build_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class HubRouteBuildJob extends BaseEntity implements Persistable<UUID> {

    @Id
    @Column(name = "hub_id", columnDefinition = "UUID")
    private UUID hubId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hub_id", nullable = false)
    private Hub hub;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HubRouteBuildJobStatus status;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Column(name = "remaining_count", nullable = false)
    private Integer remainingCount;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public static HubRouteBuildJob request(final Hub hub, final int pairCount) {
        if (hub == null || hub.getHubId() == null) {
            throw new IllegalArgumentException("hub must be persisted");
        }
        if (pairCount < 0) {
            throw new IllegalArgumentException("pairCount must not be negative");
        }
        return HubRouteBuildJob.builder()
                .hub(hub)
                .hubId(hub.getHubId())
                .status(pairCount == 0 ? HubRouteBuildJobStatus.COMPLETE : HubRouteBuildJobStatus.RUNNING)
                .totalCount(pairCount)
                .remainingCount(pairCount)
                .failedCount(0)
                .retryCount(0)
                .build();
    }

    @Override
    public UUID getId() {
        return hubId;
    }

    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }
}
