package com.jumunhasyeo.hub.hubRoute.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class RouteWeight {
    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;  // 거리 (km)

    @Column(name = "duration_minutes")
    private Integer durationMinutes;  // 예상 소요 시간 (분)

    public static RouteWeight of(BigDecimal distanceKm , Integer durationMinutes) {
        return new RouteWeight(distanceKm, durationMinutes);
    }
}
