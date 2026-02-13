package com.jumunhasyeo.hub.hubRoute.domain.service;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HubRouteDomainServicePerformanceTest {

    private static final int EXISTING_BRANCH_COUNT = 100;

    private final HubRouteDomainService domainService = new HubRouteDomainService();

    @Test
    @DisplayName("센터에 기존 지점 100개가 있을 때 새 지점 경로 생성 속도를 측정한다.")
    void build_routes_for_new_branch_with_100_existing_branches() {
        Hub centerHub = hub("센터", HubType.CENTER, 37.5, 127.0);
        for (int i = 0; i < EXISTING_BRANCH_COUNT; i++) {
            Hub existingBranch = hub("기존지점" + i, HubType.BRANCH, 37.0 + i * 0.001, 127.0 + i * 0.001);
            existingBranch.addCenterHub(centerHub);
        }
        Hub newBranch = hub("신규지점", HubType.BRANCH, 37.7, 127.2);
        newBranch.addCenterHub(centerHub);

        AtomicInteger calculatorCalls = new AtomicInteger();
        long started = System.nanoTime();

        Set<HubRoute> routes = domainService.buildRoutesForNewBranchHub(
                newBranch,
                centerHub,
                (from, to) -> {
                    calculatorCalls.incrementAndGet();
                    return RouteWeight.of(BigDecimal.valueOf(10.0), 20);
                }
        );

        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(routes).hasSize(202);
        assertThat(calculatorCalls.get()).isEqualTo(101);
        System.out.printf(
                "hub-route-domain-performance existingBranches=%d generatedRoutes=%d calculatorCalls=%d elapsedMs=%d%n",
                EXISTING_BRANCH_COUNT,
                routes.size(),
                calculatorCalls.get(),
                elapsedMillis
        );
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
