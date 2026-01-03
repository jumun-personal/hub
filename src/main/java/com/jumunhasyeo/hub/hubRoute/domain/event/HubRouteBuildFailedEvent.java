package com.jumunhasyeo.hub.hubRoute.domain.event;

import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.UUID;

@Schema(description = "HubRouteBuildFailedEvent")
@Getter
public class HubRouteBuildFailedEvent extends HubRouteDomainEvent {
    @Schema(description = "허브 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private final UUID hubId;
    @Schema(description = "중앙 허브 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private final UUID centerHubId;
    @Schema(description = "허브 타입", example = "CENTER")
    private final HubType hubType;
    @Schema(description = "실패 사유", example = "MAP API FAILED")
    private final String reason;

    public HubRouteBuildFailedEvent(UUID hubId, UUID centerHubId, HubType hubType, String reason) {
        this.hubId = hubId;
        this.centerHubId = centerHubId;
        this.hubType = hubType;
        this.reason = reason;
    }

    public static HubRouteBuildFailedEvent from(BuildRouteCommand command, String reason) {
        return new HubRouteBuildFailedEvent(
                command.hubId(),
                command.centerHubId(),
                command.type(),
                reason
        );
    }
}
