package com.jumunhasyeo.hub.hubRoute.domain.event;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxMessage;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Schema(description = "HubRouteDeletedEvent")
@AllArgsConstructor
@Getter
public class HubRouteDeletedEvent extends HubRouteDomainEvent implements OutboxMessage {
    @Schema(description = "경로 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID routeId;
    @Schema(description = "출발 허브", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID startHub;
    @Schema(description = "도착 허브", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID endHub;
    @Schema(description = "예상 시간", example = "30")
    private int durationMinutes;
    @Schema(description = "예상 거리", example = "5")
    private int distanceKm;

    public static HubRouteDeletedEvent from(HubRoute route) {
        return new HubRouteDeletedEvent(
                route.getRouteId(),
                route.getStartHub().getHubId(),
                route.getEndHub().getHubId(),
                route.getRouteWeight().getDurationMinutes(),
                (int) route.getRouteWeight().getDistanceKm().doubleValue()
        );
    }

    @Override
    public String eventName() {
        return HubRouteDeletedEvent.class.getSimpleName();
    }

    @Override
    public String eventKey() {
        return getEventKey();
    }
}
