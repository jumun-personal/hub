package com.jumunhasyeo.hub.hubRoute.application.dto.response;

import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;

import java.math.BigDecimal;

public record RouteWeightResult(
        BigDecimal distanceKm,
        Integer durationMinutes,
        MapProvider provider,
        boolean fromFallback
) {
}
