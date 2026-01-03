package com.jumunhasyeo.hub.hubRoute.infrastructure.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
public class NaverRouteResponse {

    @JsonProperty("route")
    private Route route;

    @Getter
    @NoArgsConstructor
    public static class Route {
        @JsonProperty("traoptimal")
        private List<Traoptimal> traoptimal;

        @JsonProperty("trafast")
        private List<Traoptimal> trafast;
    }

    @Getter
    @NoArgsConstructor
    public static class Traoptimal {
        @JsonProperty("summary")
        private Summary summary;
    }

    @Getter
    @NoArgsConstructor
    public static class Summary {
        @JsonProperty("distance")
        private Integer distance; // meters

        @JsonProperty("duration")
        private Long duration; // milliseconds
    }

    public BigDecimal getDistanceKm() {
        Traoptimal primary = getPrimaryRoute();
        if (primary == null || primary.summary == null) {
            return BigDecimal.ZERO;
        }
        Integer distanceMeters = primary.summary.distance;
        return BigDecimal.valueOf(distanceMeters)
                .divide(BigDecimal.valueOf(1000), 2, BigDecimal.ROUND_HALF_UP);
    }

    public Integer getDurationMinutes() {
        Traoptimal primary = getPrimaryRoute();
        if (primary == null || primary.summary == null) {
            return 0;
        }
        Long durationMillis = primary.summary.duration;
        return (int) Math.ceil(durationMillis / 60000.0);
    }

    private Traoptimal getPrimaryRoute() {
        if (route == null) {
            return null;
        }
        if (route.trafast != null && !route.trafast.isEmpty()) {
            return route.trafast.get(0);
        }
        if (route.traoptimal != null && !route.traoptimal.isEmpty()) {
            return route.traoptimal.get(0);
        }
        return null;
    }
}
