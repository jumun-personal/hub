package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.application.command.RoutePairBuildTarget;
import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import com.jumunhasyeo.hub.hubRoute.application.dto.ProviderHint;
import com.jumunhasyeo.hub.hubRoute.application.dto.RoutePurpose;
import com.jumunhasyeo.hub.hubRoute.application.dto.request.RouteWeightQuery;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.domain.entity.RouteProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RouteWorkLifecycleTest {

    @Mock
    private RoutePairLifecycleService routePairLifecycleService;
    @Mock
    private RouteProviderResolution routeProviderResolution;
    @Mock
    private RouteProviderAvailabilityService routeProviderAvailabilityService;
    @Mock
    private RouteDelayPolicy routeDelayPolicy;

    private RouteWorkLifecycle processor;

    @BeforeEach
    void setUp() {
        processor = new RouteWorkLifecycle(
                routePairLifecycleService,
                routeProviderResolution,
                routeProviderAvailabilityService,
                routeDelayPolicy
        );
        ReflectionTestUtils.setField(processor, "maxRetries", 3);
        ReflectionTestUtils.setField(processor, "nonRetryableRefreshDelay", "30m");
    }

    @Test
    @DisplayName("양방향 경로 쌍은 외부 API를 한 번만 호출하고 함께 완료한다.")
    void process_bidirectionalPair_callsApiOnce() {
        // given
        List<UUID> routeIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        RoutePairBuildTarget target = target(routeIds);
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(routePairLifecycleService.claimRoutePairBuild(routeIds)).willReturn(Optional.of(target));
        given(routeProviderResolution.resolve(any())).willReturn(
                new RouteWeightResult(BigDecimal.valueOf(12.3), 25, MapProvider.KAKAO, false)
        );

        // when
        processor.build(routeIds);

        // then
        then(routeProviderResolution).should(times(1)).resolve(any());
        ArgumentCaptor<RouteWeightQuery> queryCaptor = ArgumentCaptor.forClass(RouteWeightQuery.class);
        then(routeProviderResolution).should().resolve(queryCaptor.capture());
        assertThat(queryCaptor.getValue().providerHint()).isEqualTo(ProviderHint.PRIMARY);
        then(routePairLifecycleService).should().completeRoutePairBuild(
                org.mockito.ArgumentMatchers.eq(routeIds),
                org.mockito.ArgumentMatchers.eq(target.processingToken()),
                any(),
                org.mockito.ArgumentMatchers.eq(RouteProvider.KAKAO),
                org.mockito.ArgumentMatchers.eq(false)
        );
        then(routePairLifecycleService).should(never()).failRoutePairBuild(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("Kakao 첫 일시 장애는 Naver 전환 없이 짧은 DB 지연 재시도로 넘긴다.")
    void process_whenPrimaryRetryRequired_schedulesShortPrimaryRetry() {
        // given
        List<UUID> routeIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        RoutePairBuildTarget target = target(routeIds);
        Duration retryDelay = Duration.ofMillis(5500);
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(routePairLifecycleService.claimRoutePairBuild(routeIds)).willReturn(Optional.of(target));
        given(routeProviderResolution.resolve(any())).willThrow(
                new RoutePrimaryRetryRequiredException("retry Kakao", new RuntimeException())
        );
        given(routeDelayPolicy.primaryRetryDelay()).willReturn(retryDelay);

        // when
        processor.build(routeIds);

        // then
        then(routePairLifecycleService).should().failRoutePairBuild(routeIds, target.processingToken(), "retry Kakao", 3, retryDelay);
        then(routePairLifecycleService).should(never()).failRoutePairBuildPermanently(any(), any(), any());
    }

    @Test
    @DisplayName("Kakao 재시도 이력이 있으면 Naver Fallback을 허용하는 조회 정책을 전달한다.")
    void process_whenPrimaryAlreadyRetried_allowsFallback() {
        // given
        List<UUID> routeIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        RoutePairBuildTarget target = target(routeIds, 1);
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(routePairLifecycleService.claimRoutePairBuild(routeIds)).willReturn(Optional.of(target));
        given(routeProviderResolution.resolve(any())).willReturn(
                new RouteWeightResult(BigDecimal.TEN, 20, MapProvider.NAVER, true)
        );

        // when
        processor.build(routeIds);

        // then
        ArgumentCaptor<RouteWeightQuery> queryCaptor = ArgumentCaptor.forClass(RouteWeightQuery.class);
        then(routeProviderResolution).should().resolve(queryCaptor.capture());
        assertThat(queryCaptor.getValue().providerHint()).isEqualTo(ProviderHint.ANY);
        then(routePairLifecycleService).should().completeRoutePairBuild(
                org.mockito.ArgumentMatchers.eq(routeIds),
                org.mockito.ArgumentMatchers.eq(target.processingToken()),
                any(),
                org.mockito.ArgumentMatchers.eq(RouteProvider.NAVER),
                org.mockito.ArgumentMatchers.eq(true)
        );
    }

    @Test
    @DisplayName("Provider 토큰 부족은 실패 횟수를 증가시키지 않고 경로 쌍을 지연한다.")
    void process_whenRateLimitExhausted_defersWithoutFailure() {
        // given
        List<UUID> routeIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        RoutePairBuildTarget target = target(routeIds);
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(routePairLifecycleService.claimRoutePairBuild(routeIds)).willReturn(Optional.of(target));
        given(routeProviderResolution.resolve(any()))
                .willThrow(new RouteRateLimitExceededException(MapProvider.KAKAO));
        given(routeDelayPolicy.rateLimitDelay(Duration.ofSeconds(1)))
                .willReturn(Duration.ofMillis(1200));

        // when
        processor.build(routeIds);

        // then
        then(routePairLifecycleService).should().deferRoutePairBuild(
                routeIds,
                target.processingToken(),
                "KAKAO route rate limit exhausted",
                Duration.ofMillis(1200)
        );
        then(routePairLifecycleService).should(never()).failRoutePairBuild(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("일시 장애는 동기 재호출 없이 Backoff가 적용된 DB 재시도로 전환한다.")
    void process_whenProvidersUnavailable_schedulesDelayedRetry() {
        // given
        List<UUID> routeIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        RoutePairBuildTarget target = target(routeIds);
        Duration retryDelay = Duration.ofSeconds(11);
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(routePairLifecycleService.claimRoutePairBuild(routeIds)).willReturn(Optional.of(target));
        given(routeProviderResolution.resolve(any()))
                .willThrow(new RouteProvidersUnavailableException("providers down", new RuntimeException()));
        given(routeDelayPolicy.buildRetryDelay(0)).willReturn(retryDelay);

        // when
        processor.build(routeIds);

        // then
        then(routePairLifecycleService).should().failRoutePairBuild(
                routeIds,
                target.processingToken(),
                "providers down",
                3,
                retryDelay
        );
        then(routePairLifecycleService).should(never()).failRoutePairBuildPermanently(any(), any(), any());
    }

    @Test
    @DisplayName("잘못된 요청은 재시도하지 않고 경로 쌍을 즉시 최종 실패 처리한다.")
    void process_whenRequestRejected_failsPermanently() {
        // given
        List<UUID> routeIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        RoutePairBuildTarget target = target(routeIds);
        given(routeProviderAvailabilityService.isAllProvidersUnavailable()).willReturn(false);
        given(routePairLifecycleService.claimRoutePairBuild(routeIds)).willReturn(Optional.of(target));
        given(routeProviderResolution.resolve(any()))
                .willThrow(new RouteRequestRejectedException(MapProvider.KAKAO, "invalid route"));

        // when
        processor.build(routeIds);

        // then
        then(routePairLifecycleService).should().failRoutePairBuildPermanently(routeIds, target.processingToken(), "invalid route");
        then(routePairLifecycleService).should(never()).failRoutePairBuild(any(), any(), any(), any(Integer.class), any());
    }

    private RoutePairBuildTarget target(List<UUID> routeIds) {
        return target(routeIds, 0);
    }

    private RoutePairBuildTarget target(List<UUID> routeIds, int retryCount) {
        return new RoutePairBuildTarget(
                routeIds,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Coordinate.of(37.5, 127.0),
                Coordinate.of(35.8, 128.6),
                RoutePurpose.CENTER_TO_CENTER,
                retryCount,
                UUID.randomUUID()
        );
    }
}
