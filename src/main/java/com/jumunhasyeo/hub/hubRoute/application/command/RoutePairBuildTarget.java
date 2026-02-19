package com.jumunhasyeo.hub.hubRoute.application.command;

import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.application.dto.RoutePurpose;

import java.util.List;
import java.util.UUID;

public record RoutePairBuildTarget(
        List<UUID> routeIds,
        UUID buildHubId,
        UUID startHubId,
        Coordinate startCoordinate,
        Coordinate endCoordinate,
        RoutePurpose purpose,
        int retryCount
) {
}
