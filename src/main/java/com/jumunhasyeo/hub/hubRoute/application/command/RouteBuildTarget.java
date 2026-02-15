package com.jumunhasyeo.hub.hubRoute.application.command;

import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.application.dto.RoutePurpose;

import java.util.UUID;

public record RouteBuildTarget(
        UUID routeId,
        UUID buildHubId,
        UUID startHubId,
        Coordinate startCoordinate,
        UUID endHubId,
        Coordinate endCoordinate,
        RoutePurpose purpose
) {
}
