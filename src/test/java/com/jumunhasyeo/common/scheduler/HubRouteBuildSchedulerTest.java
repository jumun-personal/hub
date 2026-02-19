package com.jumunhasyeo.common.scheduler;

import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.application.command.RouteBuildTarget;
import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import com.jumunhasyeo.hub.hubRoute.application.dto.RoutePurpose;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderAvailabilityService;
import com.jumunhasyeo.hub.hubRoute.application.service.RoutePairBuildProcessor;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteWeightApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
class HubRouteBuildSchedulerTest {

    @Mock
    private HubRouteService hubRouteService;

    @Mock
    private RouteWeightApiService routeWeightApiService;

    @Mock
    private RouteProviderAvailabilityService routeProviderAvailabilityService;
    @Mock
    private RoutePairBuildProcessor routePairBuildProcessor;

    private HubRouteBuildScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HubRouteBuildScheduler(
                hubRouteService,
                routeProviderAvailabilityService,
                routePairBuildProcessor
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
        then(hubRouteService).should(never()).findRouteBuildRecoveryTargets(anyInt(), any(Duration.class));
        then(routePairBuildProcessor).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("재시도 시간이 지난 경로 쌍을 복구 처리기에 전달한다")
    void buildPendingRoutes_whenRecoveryIsDue_delegatesRoutePair() {
        // given
        UUID routeId = UUID.randomUUID();
        List<UUID> routePairIds = List.of(routeId, UUID.randomUUID());
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(hubRouteService.findRouteBuildRecoveryTargets(eq(10), eq(Duration.ofMinutes(5))))
                .willReturn(List.of(routeId));
        given(hubRouteService.findRoutePairIds(routeId)).willReturn(routePairIds);

        // when
        scheduler.buildPendingRoutes();

        // then
        then(routePairBuildProcessor).should().process(routePairIds);
    }

    private RouteBuildTarget routeBuildTarget(UUID routeId) {
        return new RouteBuildTarget(
                routeId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Coordinate.of(37.5, 127.0),
                UUID.randomUUID(),
                Coordinate.of(35.8, 128.6),
                RoutePurpose.CENTER_TO_CENTER
        );
    }
}
