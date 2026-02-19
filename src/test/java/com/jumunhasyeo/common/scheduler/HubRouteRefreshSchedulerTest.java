package com.jumunhasyeo.common.scheduler;

import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.application.service.RoutePairBuildProcessor;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderAvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class HubRouteRefreshSchedulerTest {

    @Mock
    private HubRouteService hubRouteService;
    @Mock
    private RouteProviderAvailabilityService routeProviderAvailabilityService;
    @Mock
    private RoutePairBuildProcessor routePairBuildProcessor;

    private HubRouteRefreshScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HubRouteRefreshScheduler(
                hubRouteService,
                routeProviderAvailabilityService,
                routePairBuildProcessor
        );
        ReflectionTestUtils.setField(scheduler, "batchSize", 10);
        ReflectionTestUtils.setField(scheduler, "staleTimeout", "5m");
    }

    @Test
    @DisplayName("신규 생성이나 재시도 작업이 있으면 주기 갱신이 양보한다.")
    void refreshDueRoutes_whenBuildWorkExists_skipsRefresh() {
        // given
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(hubRouteService.hasActiveBuildWork()).willReturn(true);

        // when
        scheduler.refreshDueRoutes();

        // then
        then(hubRouteService).should(never()).findRouteRefreshTargets(10, Duration.ofMinutes(5));
        then(routePairBuildProcessor).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("갱신 시간이 지난 방향 경로를 경로 쌍으로 묶어 갱신 처리기에 전달한다.")
    void refreshDueRoutes_whenRefreshIsDue_processesRoutePair() {
        // given
        UUID candidate = UUID.randomUUID();
        List<UUID> routePairIds = List.of(candidate, UUID.randomUUID());
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(hubRouteService.hasActiveBuildWork()).willReturn(false);
        given(hubRouteService.findRouteRefreshTargets(10, Duration.ofMinutes(5)))
                .willReturn(List.of(candidate));
        given(hubRouteService.findRoutePairIds(candidate)).willReturn(routePairIds);

        // when
        scheduler.refreshDueRoutes();

        // then
        then(routePairBuildProcessor).should().processRefresh(routePairIds);
    }
}
