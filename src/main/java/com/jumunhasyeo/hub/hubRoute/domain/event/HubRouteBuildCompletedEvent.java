package com.jumunhasyeo.hub.hubRoute.domain.event;

import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.UUID;

@Schema(description = "HubRouteBuildCompletedEvent")
@Getter
public class HubRouteBuildCompletedEvent extends HubRouteDomainEvent {
    @Schema(description = "허브 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private final UUID hubId;
    @Schema(description = "중앙 허브 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private final UUID centerHubId;
    @Schema(description = "허브 타입", example = "CENTER")
    private final HubType hubType;

    public HubRouteBuildCompletedEvent(UUID hubId, UUID centerHubId, HubType hubType) {
        this.hubId = hubId;
        this.centerHubId = centerHubId;
        this.hubType = hubType;
    }

    public static HubRouteBuildCompletedEvent from(BuildRouteCommand command) {
        return new HubRouteBuildCompletedEvent(
                command.hubId(),
                command.centerHubId(),
                command.type()
        );
    }

}
