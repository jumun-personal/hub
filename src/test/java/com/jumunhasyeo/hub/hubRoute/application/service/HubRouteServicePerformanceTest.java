package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.application.HubRouteEventPublisher;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;
import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import com.jumunhasyeo.hub.hubRoute.domain.service.HubRouteDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HubRouteServicePerformanceTest {

    private static final int EXISTING_BRANCH_COUNT = 100;

    @Mock
    private HubRepository hubRepository;

    @Mock
    private RouteWeightApiService routeWeightApi;

    @Mock
    private HubRouteRepository hubRouteRepository;

    @Mock
    private HubRouteEventPublisher hubRouteEventPublisher;

    @Test
    @DisplayName("센터에 기존 지점 100개가 있을 때 새 지점 경로 생성 서비스 속도를 측정한다.")
    void build_routes_for_new_branch_with_100_existing_branches_service() {
        HubRouteService service = new HubRouteService(
                hubRepository,
                routeWeightApi,
                hubRouteRepository,
                new HubRouteDomainService(),
                hubRouteEventPublisher
        );
        Hub centerHub = hub("센터", HubType.CENTER, 37.5, 127.0);
        for (int i = 0; i < EXISTING_BRANCH_COUNT; i++) {
            Hub existingBranch = hub("기존지점" + i, HubType.BRANCH, 37.0 + i * 0.001, 127.0 + i * 0.001);
            existingBranch.addCenterHub(centerHub);
        }
        Hub newBranch = hub("신규지점", HubType.BRANCH, 37.7, 127.2);
        newBranch.addCenterHub(centerHub);
        BuildRouteCommand command = new BuildRouteCommand(
                centerHub.getHubId(),
                newBranch.getHubId(),
                newBranch.getName(),
                newBranch.getAddress(),
                HubType.BRANCH
        );

        given(hubRepository.findByIdIncludingCreating(newBranch.getHubId())).willReturn(Optional.of(newBranch));
        given(hubRepository.findById(centerHub.getHubId())).willReturn(Optional.of(centerHub));
        given(hubRouteRepository.findByStartHubOrEndHub(newBranch, newBranch)).willReturn(java.util.List.of());
        given(routeWeightApi.getRouteInfo(any()))
                .willReturn(new RouteWeightResult(BigDecimal.valueOf(10.0), 20, MapProvider.KAKAO, false));

        long started = System.nanoTime();
        service.buildRoutesForNewHub(command);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        ArgumentCaptor<Set<HubRoute>> routeCaptor = ArgumentCaptor.forClass(Set.class);
        verify(hubRouteRepository).insertIgnore(routeCaptor.capture());
        Set<HubRoute> routes = routeCaptor.getValue();

        assertThat(routes).hasSize(202);
        verify(routeWeightApi, times(101)).getRouteInfo(any());
        System.out.printf(
                "hub-route-service-performance existingBranches=%d generatedRoutes=%d externalApiCalls=%d elapsedMs=%d%n",
                EXISTING_BRANCH_COUNT,
                routes.size(),
                101,
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
