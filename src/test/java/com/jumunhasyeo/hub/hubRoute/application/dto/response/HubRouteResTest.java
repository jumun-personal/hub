package com.jumunhasyeo.hub.hubRoute.application.dto.response;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HubRouteResTest {

    @Test
    @DisplayName("HubRoute를 HubRouteRes로 변환한다.")
    void from_success() {
        Hub start = hub("출발", HubType.CENTER, 37.5, 127.0);
        Hub end = hub("도착", HubType.BRANCH, 37.6, 127.1);
        HubRoute route = HubRoute.ofSelfId(UUID.randomUUID(), start, end, RouteWeight.of(BigDecimal.valueOf(14.9), 45));

        HubRouteRes result = HubRouteRes.from(route);

        assertThat(result.routeId()).isEqualTo(route.getRouteId());
        assertThat(result.startHub()).isEqualTo(start.getHubId());
        assertThat(result.endHub()).isEqualTo(end.getHubId());
        assertThat(result.durationMinutes()).isEqualTo(45);
        assertThat(result.distanceKm()).isEqualTo(14);
    }

    private Hub hub(String name, HubType type, double lat, double lng) {
        return Hub.builder()
                .hubId(UUID.randomUUID())
                .name(name)
                .hubType(type)
                .address(Address.of("주소", Coordinate.of(lat, lng)))
                .centerHubRelations(new HashSet<>())
                .branchHubRelations(new HashSet<>())
                .build();
    }
}
