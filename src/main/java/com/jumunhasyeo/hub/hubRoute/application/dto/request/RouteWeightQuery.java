package com.jumunhasyeo.hub.hubRoute.application.dto.request;

import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.application.dto.ProviderHint;
import com.jumunhasyeo.hub.hubRoute.application.dto.RoutePurpose;

import java.util.UUID;

public record RouteWeightQuery(
        UUID hubId,
        Coordinate start,
        Coordinate end,
        RoutePurpose purpose,
        ProviderHint providerHint
) {
}
