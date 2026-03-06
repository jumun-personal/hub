package com.jumunhasyeo.common.scheduler;

import com.jumunhasyeo.hub.hubRoute.application.command.RoutePairBuildTarget;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderAvailabilityService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteWorkLifecycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class HubRouteBuildSchedulerTest {

    @Mock
    private HubRouteService hubRouteService;
    @Mock
    private RouteProviderAvailabilityService routeProviderAvailabilityService;
    @Mock
    private RouteWorkLifecycle routeWorkLifecycle;

    private HubRouteBuildScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HubRouteBuildScheduler(
                hubRouteService,
                routeProviderAvailabilityService,
                routeWorkLifecycle
        );
        ReflectionTestUtils.setField(scheduler, "staleProcessingTimeout", "5m");
    }

    @Test
    @DisplayName("지도 Provider 장애 게이트가 켜져 있으면 작업 조회를 건너뛴다")
    void buildPendingRoutes_whenProvidersUnavailable_skipsLookup() {
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(true);

        scheduler.buildPendingRoutes();

        then(hubRouteService).shouldHaveNoInteractions();
        then(routeWorkLifecycle).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("한 번의 스케줄 실행에서 경로쌍 하나를 동기로 끝까지 처리한다")
    void buildPendingRoutes_processesOnePairSynchronously() {
        UUID representativeRouteId = UUID.randomUUID();
        List<UUID> routeIds = List.of(representativeRouteId, UUID.randomUUID());
        RoutePairBuildTarget target = new RoutePairBuildTarget(
                routeIds, UUID.randomUUID(), UUID.randomUUID(), null, null, null, 0
        );
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(hubRouteService.findRunningJobBuildTargets(1, Duration.ofMinutes(5)))
                .willReturn(List.of(representativeRouteId));
        given(hubRouteService.findRoutePairIds(representativeRouteId)).willReturn(routeIds);
        given(routeWorkLifecycle.claimBuild(routeIds)).willReturn(Optional.of(target));

        scheduler.buildPendingRoutes();

        then(routeWorkLifecycle).should().build(target);
    }

    @Test
    @DisplayName("실행할 경로가 없으면 선점과 처리를 하지 않는다")
    void buildPendingRoutes_whenNoTargets_doesNothing() {
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(hubRouteService.findRunningJobBuildTargets(1, Duration.ofMinutes(5)))
                .willReturn(List.of());

        scheduler.buildPendingRoutes();

        then(hubRouteService).should(never()).findRoutePairIds(any());
        then(routeWorkLifecycle).shouldHaveNoInteractions();
    }
}
