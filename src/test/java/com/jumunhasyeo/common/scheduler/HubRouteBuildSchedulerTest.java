package com.jumunhasyeo.common.scheduler;

import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderAvailabilityService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteWorkLifecycle;
import com.jumunhasyeo.hub.hubRoute.application.command.RoutePairBuildTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class HubRouteBuildSchedulerTest {

    @Mock
    private HubRouteService hubRouteService;

    @Mock
    private RouteProviderAvailabilityService routeProviderAvailabilityService;
    @Mock
    private RouteWorkLifecycle routeWorkLifecycle;
    @Mock
    private ThreadPoolTaskExecutor hubRouteBuildExecutor;

    private HubRouteBuildScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HubRouteBuildScheduler(
                hubRouteService,
                routeProviderAvailabilityService,
                routeWorkLifecycle,
                hubRouteBuildExecutor
        );
        ReflectionTestUtils.setField(scheduler, "batchSize", 10);
        ReflectionTestUtils.setField(scheduler, "staleProcessingTimeout", "5m");
    }

    @Test
    @DisplayName("지도 Provider 장애 게이트가 켜져 있으면 신규 경로 claim을 수행하지 않는다")
    void buildPendingRoutes_whenProvidersUnavailable_skipsClaim() {
        // given
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(true);

        // when
        scheduler.buildPendingRoutes();

        // then
        then(hubRouteService).should(never()).findRunningJobBuildTargets(anyInt(), any(Duration.class));
        then(routeWorkLifecycle).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("재시도 시간이 지난 경로 쌍을 복구 처리기에 전달한다")
    void buildPendingRoutes_whenRecoveryIsDue_delegatesRoutePair() {
        // given
        UUID routeId = UUID.randomUUID();
        List<UUID> routePairIds = List.of(routeId, UUID.randomUUID());
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        RoutePairBuildTarget target = new RoutePairBuildTarget(
                routePairIds, UUID.randomUUID(), UUID.randomUUID(), null, null, null, 0, UUID.randomUUID()
        );
        given(hubRouteBuildExecutor.getMaxPoolSize()).willReturn(10);
        given(hubRouteBuildExecutor.getActiveCount()).willReturn(0);
        given(hubRouteService.findRunningJobBuildTargets(eq(20), eq(Duration.ofMinutes(5))))
                .willReturn(List.of(routeId));
        given(hubRouteService.findRoutePairIds(routeId)).willReturn(routePairIds);
        given(routeWorkLifecycle.claimBuild(routePairIds)).willReturn(Optional.of(target));

        // when
        scheduler.buildPendingRoutes();

        // then
        then(routeWorkLifecycle).should().claimBuild(routePairIds);
        then(hubRouteBuildExecutor).should().execute(any(Runnable.class));
    }

    @Test
    @DisplayName("실행 중인 Worker가 7개면 빈 슬롯 3개만 DB에서 선점한다")
    void buildPendingRoutes_whenSevenWorkersAreActive_claimsOnlyThreePairs() {
        // given
        UUID firstRouteId = UUID.randomUUID();
        UUID secondRouteId = UUID.randomUUID();
        UUID thirdRouteId = UUID.randomUUID();
        List<UUID> firstPair = List.of(firstRouteId, UUID.randomUUID());
        List<UUID> secondPair = List.of(secondRouteId, UUID.randomUUID());
        List<UUID> thirdPair = List.of(thirdRouteId, UUID.randomUUID());
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(hubRouteBuildExecutor.getMaxPoolSize()).willReturn(10);
        given(hubRouteBuildExecutor.getActiveCount()).willReturn(7);
        given(hubRouteService.findRunningJobBuildTargets(eq(6), eq(Duration.ofMinutes(5))))
                .willReturn(List.of(firstRouteId, secondRouteId, thirdRouteId));
        given(hubRouteService.findRoutePairIds(firstRouteId)).willReturn(firstPair);
        given(hubRouteService.findRoutePairIds(secondRouteId)).willReturn(secondPair);
        given(hubRouteService.findRoutePairIds(thirdRouteId)).willReturn(thirdPair);
        given(routeWorkLifecycle.claimBuild(any())).willAnswer(invocation -> Optional.of(new RoutePairBuildTarget(
                invocation.getArgument(0), UUID.randomUUID(), UUID.randomUUID(), null, null, null, 0, UUID.randomUUID()
        )));

        // when
        scheduler.buildPendingRoutes();

        // then
        then(routeWorkLifecycle).should().claimBuild(firstPair);
        then(routeWorkLifecycle).should().claimBuild(secondPair);
        then(routeWorkLifecycle).should().claimBuild(thirdPair);
        then(hubRouteBuildExecutor).should(times(3)).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("Executor 제출이 거절되면 선점한 경로 쌍을 즉시 PENDING으로 되돌린다")
    void buildPendingRoutes_whenExecutorRejects_releasesClaim() {
        // given
        UUID routeId = UUID.randomUUID();
        List<UUID> routePairIds = List.of(routeId, UUID.randomUUID());
        RoutePairBuildTarget target = new RoutePairBuildTarget(
                routePairIds, UUID.randomUUID(), UUID.randomUUID(), null, null, null, 0, UUID.randomUUID()
        );
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(hubRouteBuildExecutor.getMaxPoolSize()).willReturn(10);
        given(hubRouteBuildExecutor.getActiveCount()).willReturn(0);
        given(hubRouteService.findRunningJobBuildTargets(eq(20), eq(Duration.ofMinutes(5))))
                .willReturn(List.of(routeId));
        given(hubRouteService.findRoutePairIds(routeId)).willReturn(routePairIds);
        given(routeWorkLifecycle.claimBuild(routePairIds)).willReturn(Optional.of(target));
        doThrow(new TaskRejectedException("executor saturated"))
                .when(hubRouteBuildExecutor).execute(any(Runnable.class));

        // when
        scheduler.buildPendingRoutes();

        // then
        then(routeWorkLifecycle).should().releaseBuildClaim(target, "hub route build executor rejected task");
    }

}
