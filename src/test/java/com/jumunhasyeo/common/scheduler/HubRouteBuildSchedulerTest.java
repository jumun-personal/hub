package com.jumunhasyeo.common.scheduler;

import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.application.command.RouteBuildTarget;
import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import com.jumunhasyeo.hub.hubRoute.application.dto.RoutePurpose;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderAvailabilityService;
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

    private HubRouteBuildScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HubRouteBuildScheduler(
                hubRouteService,
                routeWeightApiService,
                routeProviderAvailabilityService
        );
        ReflectionTestUtils.setField(scheduler, "batchSize", 10);
        ReflectionTestUtils.setField(scheduler, "staleProcessingTimeout", "5m");
        ReflectionTestUtils.setField(scheduler, "retryBackoff", "30s");
        ReflectionTestUtils.setField(scheduler, "maxRetries", 3);
    }

    @Test
    @DisplayName("지도 Provider 장애 게이트가 켜져 있으면 신규 경로 claim을 수행하지 않는다")
    void buildPendingRoutes_whenProvidersUnavailable_skipsClaim() {
        // given
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(true);

        // when
        scheduler.buildPendingRoutes();

        // then
        then(hubRouteService).should(never()).claimRouteBuildTargets(anyInt(), any(Duration.class));
        then(routeWeightApiService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("claim한 경로의 지도 API 호출이 성공하면 경로 가중치 완료 저장을 요청한다")
    void buildPendingRoutes_whenApiSucceeds_completesRouteBuild() {
        // given
        UUID routeId = UUID.randomUUID();
        RouteBuildTarget target = routeBuildTarget(routeId);
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(hubRouteService.claimRouteBuildTargets(eq(10), eq(Duration.ofMinutes(5))))
                .willReturn(List.of(routeId));
        given(hubRouteService.getRouteBuildTarget(routeId)).willReturn(Optional.of(target));
        given(routeWeightApiService.getRouteInfo(any()))
                .willReturn(new RouteWeightResult(BigDecimal.valueOf(12.3), 25, MapProvider.KAKAO, false));

        // when
        scheduler.buildPendingRoutes();

        // then
        then(hubRouteService).should().completeRouteBuild(eq(routeId), any());
        then(hubRouteService).should(never()).failRouteBuild(any(), any(), anyInt(), any(Duration.class));
    }

    @Test
    @DisplayName("claim한 경로의 지도 API 호출이 실패하면 경로 재시도 저장을 요청한다")
    void buildPendingRoutes_whenApiFails_recordsRouteBuildFailure() {
        // given
        UUID routeId = UUID.randomUUID();
        RouteBuildTarget target = routeBuildTarget(routeId);
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(hubRouteService.claimRouteBuildTargets(eq(10), eq(Duration.ofMinutes(5))))
                .willReturn(List.of(routeId));
        given(hubRouteService.getRouteBuildTarget(routeId)).willReturn(Optional.of(target));
        given(routeWeightApiService.getRouteInfo(any())).willThrow(new RuntimeException("map down"));

        // when
        scheduler.buildPendingRoutes();

        // then
        then(hubRouteService).should().failRouteBuild(routeId, "map down", 3, Duration.ofSeconds(30));
        then(hubRouteService).should(never()).completeRouteBuild(any(), any());
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
