package com.jumunhasyeo.hub.hub.domain.event;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxMessage;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.UUID;

@Getter
@Schema(description = "HubDeletedEvent")
public class HubDeletedEvent extends HubDomainEvent implements OutboxMessage {
    @Schema(description = "허브 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private final UUID hubId;
    @Schema(description = "허브 이름", example = "송파B")
    private final String name;
    @Schema(description = "허브 타입", example = "CENTER")
    private final HubType hubType;
    @Schema(description = "삭제자", example = "1L")
    private final Long deletedBy;

    public HubDeletedEvent(UUID hubId, String name, HubType hubType, Long deletedBy) {
        this.hubId = hubId;
        this.name = name;
        this.hubType = hubType;
        this.deletedBy = deletedBy;
    }

    public static HubDeletedEvent of(UUID hubId, String hubName, HubType hubType, Long deletedBy) {
        return new HubDeletedEvent(hubId, hubName, hubType, deletedBy);
    }

    public static HubDeletedEvent from(Hub hub, Long userId) {
        return new HubDeletedEvent(
                hub.getHubId(),
                hub.getName(),
                hub.getHubType(),
                userId
        );
    }

    @Override
    public String eventName() {
        return HubDeletedEvent.class.getSimpleName();
    }

    @Override
    public String eventKey() {
        return getEventKey();
    }
}
