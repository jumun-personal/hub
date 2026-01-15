package com.jumunhasyeo.hub.hub.domain.event;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxMessage;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.UUID;

@Schema(description = "HubCreatedEvent")
@Getter
public final class HubCreatedEvent extends HubDomainEvent implements OutboxMessage {

    @Schema(description = "중앙 허브 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private final UUID centerHubId;
    @Schema(description = "허브 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private final UUID hubId;
    @Schema(description = "허브 이름", example = "송파B")
    private final String name;
    @Schema(description = "주소", example = "(object)")
    private final Address address;
    @Schema(description = "허브 타입", example = "CENTER")
    private final HubType type;

    private HubCreatedEvent(UUID centerHubId, UUID hubId, String name, Address address, HubType type) {
        this.centerHubId = centerHubId;
        this.hubId = hubId;
        this.name = name;
        this.address = address;
        this.type = type;
    }

    public static HubCreatedEvent centerHub(Hub hub) {
        return new HubCreatedEvent(
                null,
                hub.getHubId(),
                hub.getName(),
                hub.getAddress(),
                hub.getHubType()
        );
    }

    public static HubCreatedEvent branchHub(Hub hub, UUID centerHubId) {
        return new HubCreatedEvent(
                centerHubId,
                hub.getHubId(),
                hub.getName(),
                hub.getAddress(),
                hub.getHubType()
        );
    }

    @Override
    public String eventName() {
        return HubCreatedEvent.class.getSimpleName();
    }

    @Override
    public String eventKey() {
        return getEventKey();
    }
}
