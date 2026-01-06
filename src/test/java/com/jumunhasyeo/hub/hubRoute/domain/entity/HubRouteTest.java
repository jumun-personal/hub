package com.jumunhasyeo.hub.hubRoute.domain.entity;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HubRouteTest {

    @Test
    @DisplayName("of로 HubRoute를 생성할 수 있다.")
    void of_success() {
        Hub from = hub("센터", HubType.CENTER, 1.0, 1.0);
        Hub to = hub("지점", HubType.BRANCH, 2.0, 2.0);
        RouteWeight weight = RouteWeight.of(BigDecimal.valueOf(12.3), 20);

        HubRoute route = HubRoute.of(from, to, weight);

        assertThat(route.getStartHub()).isEqualTo(from);
        assertThat(route.getEndHub()).isEqualTo(to);
        assertThat(route.getRouteWeight()).isEqualTo(weight);
    }

    @Test
    @DisplayName("createTwoWay는 양방향 경로 2개를 만든다.")
    void createTwoWay_success() {
        Hub from = hub("센터", HubType.CENTER, 1.0, 1.0);
        Hub to = hub("지점", HubType.BRANCH, 2.0, 2.0);
        RouteWeight weight = RouteWeight.of(BigDecimal.valueOf(8.8), 13);

        Set<HubRoute> routes = HubRoute.createTwoWay(from, to, weight);

        assertThat(routes).hasSize(2);
        assertThat(routes).allMatch(r -> r.getRouteWeight().equals(weight));
        assertThat(routes).extracting(r -> r.getStartHub().getHubId())
                .containsExactlyInAnyOrder(from.getHubId(), to.getHubId());
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
