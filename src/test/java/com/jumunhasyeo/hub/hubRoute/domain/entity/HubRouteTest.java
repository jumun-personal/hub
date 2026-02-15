package com.jumunhasyeo.hub.hubRoute.domain.entity;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HubRouteTest {

    @Test
    @DisplayName("of로 HubRoute를 생성할 수 있다.")
    void of_success() {
        // given
        Hub from = hub("센터", HubType.CENTER, 1.0, 1.0);
        Hub to = hub("지점", HubType.BRANCH, 2.0, 2.0);
        RouteWeight weight = RouteWeight.of(BigDecimal.valueOf(12.3), 20);

        // when
        HubRoute route = HubRoute.of(from, to, weight);

        // then
        assertThat(route.getStartHub()).isEqualTo(from);
        assertThat(route.getEndHub()).isEqualTo(to);
        assertThat(route.getRouteWeight()).isEqualTo(weight);
        assertThat(route.getStatus()).isEqualTo(HubRouteStatus.COMPLETE);
    }

    @Test
    @DisplayName("createTwoWay는 양방향 경로 2개를 만든다.")
    void createTwoWay_success() {
        // given
        Hub from = hub("센터", HubType.CENTER, 1.0, 1.0);
        Hub to = hub("지점", HubType.BRANCH, 2.0, 2.0);
        RouteWeight weight = RouteWeight.of(BigDecimal.valueOf(8.8), 13);

        // when
        Set<HubRoute> routes = HubRoute.createTwoWay(from, to, weight);

        // then
        assertThat(routes).hasSize(2);
        assertThat(routes).allMatch(r -> r.getRouteWeight().equals(weight));
        assertThat(routes).allMatch(HubRoute::isComplete);
        assertThat(routes).extracting(r -> r.getStartHub().getHubId())
                .containsExactlyInAnyOrder(from.getHubId(), to.getHubId());
    }

    @Test
    @DisplayName("skeleton 경로는 PENDING 상태와 비어 있는 가중치로 생성된다")
    void skeleton_createsPendingRouteWithoutWeight() {
        // given
        UUID buildHubId = UUID.randomUUID();
        Hub from = hub("센터", HubType.CENTER, 1.0, 1.0);
        Hub to = hub("지점", HubType.BRANCH, 2.0, 2.0);

        // when
        HubRoute route = HubRoute.skeleton(buildHubId, from, to);

        // then
        assertThat(route.getBuildHubId()).isEqualTo(buildHubId);
        assertThat(route.getRouteId()).isNotNull();
        assertThat(route.getStatus()).isEqualTo(HubRouteStatus.PENDING);
        assertThat(route.getRouteWeight()).isNull();
    }

    @Test
    @DisplayName("claim 후 경로 가중치 저장을 완료하면 COMPLETE 상태가 된다")
    void complete_afterClaim_changesStatusToComplete() {
        // given
        HubRoute route = HubRoute.skeleton(UUID.randomUUID(), hub("센터", HubType.CENTER, 1.0, 1.0), hub("지점", HubType.BRANCH, 2.0, 2.0));
        RouteWeight weight = RouteWeight.of(BigDecimal.valueOf(15.5), 33);
        route.claimProcessing();

        // when
        route.complete(weight);

        // then
        assertThat(route.getStatus()).isEqualTo(HubRouteStatus.COMPLETE);
        assertThat(route.getRouteWeight()).isEqualTo(weight);
        assertThat(route.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("최대 재시도 전 실패는 PENDING으로 되돌리고 다음 재시도 시간을 기록한다")
    void failOrRetry_beforeMaxRetries_returnsPending() {
        // given
        HubRoute route = HubRoute.skeleton(UUID.randomUUID(), hub("센터", HubType.CENTER, 1.0, 1.0), hub("지점", HubType.BRANCH, 2.0, 2.0));
        LocalDateTime nextRetryAt = LocalDateTime.of(2026, 6, 30, 10, 0);
        route.claimProcessing();

        // when
        boolean finalFailed = route.failOrRetry("map down", 3, nextRetryAt);

        // then
        assertThat(finalFailed).isFalse();
        assertThat(route.getStatus()).isEqualTo(HubRouteStatus.PENDING);
        assertThat(route.getRetryCount()).isEqualTo(1);
        assertThat(route.getNextRetryAt()).isEqualTo(nextRetryAt);
    }

    @Test
    @DisplayName("최대 재시도에 도달한 실패는 FAILED 상태가 된다")
    void failOrRetry_whenMaxRetriesReached_changesStatusToFailed() {
        // given
        HubRoute route = HubRoute.skeleton(UUID.randomUUID(), hub("센터", HubType.CENTER, 1.0, 1.0), hub("지점", HubType.BRANCH, 2.0, 2.0));
        route.claimProcessing();

        // when
        boolean finalFailed = route.failOrRetry("map down", 1, LocalDateTime.of(2026, 6, 30, 10, 0));

        // then
        assertThat(finalFailed).isTrue();
        assertThat(route.getStatus()).isEqualTo(HubRouteStatus.FAILED);
        assertThat(route.getNextRetryAt()).isNull();
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
