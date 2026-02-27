package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.application.HubRouteEventPublisher;
import com.jumunhasyeo.hub.hubRoute.application.command.RoutePairBuildTarget;
import com.jumunhasyeo.hub.hubRoute.application.dto.RoutePurpose;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus;
import com.jumunhasyeo.hub.hubRoute.domain.entity.RouteProvider;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RoutePairLifecycleServiceTest {

    @Mock
    private HubRepository hubRepository;
    @Mock
    private HubRouteRepository hubRouteRepository;
    @Mock
    private HubRouteEventPublisher hubRouteEventPublisher;

    private RoutePairLifecycleService service;
    private Hub center1;
    private Hub center2;

    @BeforeEach
    void setUp() {
        service = new RoutePairLifecycleService(hubRepository, hubRouteRepository, hubRouteEventPublisher);
        ReflectionTestUtils.setField(service, "eventRecoveryDelay", "5m");
        ReflectionTestUtils.setField(service, "routeRefreshInterval", "5m");
        ReflectionTestUtils.setField(service, "fallbackReconcileDelay", "1m");
        center1 = hub("센터1", 37.5, 127.0);
        center2 = hub("센터2", 35.8, 128.6);
    }

    @Test
    @DisplayName("경로 쌍 일부가 조회되지 않으면 어느 경로도 작업 상태로 선점하지 않는다")
    void claimRoutePairBuildRejectsMissingRoute() {
        // given
        HubRoute route = route(center1, center2);
        List<UUID> routeIds = List.of(route.getRouteId(), UUID.randomUUID());
        given(hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds)).willReturn(List.of(route));

        // when
        Optional<RoutePairBuildTarget> result = service.claimRoutePairBuild(routeIds);

        // then
        assertThat(result).isEmpty();
        assertThat(route.getStatus()).isEqualTo(HubRouteStatus.PENDING);
        then(hubRouteRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("대기 중인 양방향 경로는 하나의 경로 쌍 작업으로 함께 선점한다")
    void claimRoutePairBuildClaimsBothDirections() {
        // given
        HubRoute outbound = route(center1, center2);
        HubRoute inbound = route(center2, center1);
        List<UUID> routeIds = List.of(outbound.getRouteId(), inbound.getRouteId());
        given(hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds))
                .willReturn(List.of(outbound, inbound));

        // when
        Optional<RoutePairBuildTarget> result = service.claimRoutePairBuild(routeIds);

        // then
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().purpose()).isEqualTo(RoutePurpose.CENTER_TO_CENTER);
        assertThat(outbound.getStatus()).isEqualTo(HubRouteStatus.PROCESSING);
        assertThat(inbound.getStatus()).isEqualTo(HubRouteStatus.PROCESSING);
        then(hubRouteRepository).should().saveAll(List.of(outbound, inbound));
    }

    @Test
    @DisplayName("아직 복구 시점이 아닌 처리 중 경로가 섞이면 경로 쌍을 선점하지 않는다")
    void claimRoutePairBuildRejectsFreshProcessingRoute() {
        // given
        HubRoute processing = route(center1, center2);
        HubRoute pending = route(center2, center1);
        processing.claimProcessing();
        ReflectionTestUtils.setField(processing, "modifiedAt", LocalDateTime.now());
        List<UUID> routeIds = List.of(processing.getRouteId(), pending.getRouteId());
        given(hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds))
                .willReturn(List.of(processing, pending));

        // when
        Optional<RoutePairBuildTarget> result = service.claimRoutePairBuild(routeIds);

        // then
        assertThat(result).isEmpty();
        assertThat(pending.getStatus()).isEqualTo(HubRouteStatus.PENDING);
        then(hubRouteRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("복구 시간이 지난 처리 중 경로는 경로 쌍과 함께 다시 선점할 수 있다")
    void claimRoutePairBuildAllowsStaleProcessingRoute() {
        // given
        HubRoute stale = route(center1, center2);
        HubRoute pending = route(center2, center1);
        stale.claimProcessing();
        ReflectionTestUtils.setField(stale, "modifiedAt", LocalDateTime.now().minusMinutes(6));
        List<UUID> routeIds = List.of(stale.getRouteId(), pending.getRouteId());
        given(hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds))
                .willReturn(List.of(stale, pending));

        // when
        Optional<RoutePairBuildTarget> result = service.claimRoutePairBuild(routeIds);

        // then
        assertThat(result).isPresent();
        assertThat(stale.getStatus()).isEqualTo(HubRouteStatus.PROCESSING);
        assertThat(pending.getStatus()).isEqualTo(HubRouteStatus.PROCESSING);
        then(hubRouteRepository).should().saveAll(List.of(stale, pending));
    }

    @Test
    @DisplayName("경로 쌍 완료 시 허브 잠금을 먼저 획득하고 새로 완료된 경로만 생성 이벤트로 발행한다")
    void completeRoutePairBuildLocksHubAndPublishesOnlyNewCompletion() {
        // given
        HubRoute pending = route(center1, center2);
        HubRoute completed = route(center2, center1);
        completed.complete(weight());
        List<UUID> routeIds = List.of(pending.getRouteId(), completed.getRouteId());
        given(hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds))
                .willReturn(List.of(pending, completed));
        given(hubRouteRepository.hasIncompleteRoutes(center1.getHubId())).willReturn(false);
        given(hubRepository.findByIdIncludingCreating(center1.getHubId())).willReturn(Optional.of(center1));

        // when
        service.completeRoutePairBuild(routeIds, weight(), RouteProvider.KAKAO, false);

        // then
        assertThat(pending.isComplete()).isTrue();
        then(hubRouteRepository).should().saveAll(List.of(pending));
        then(hubRouteEventPublisher).should().publishRouteCreatedEvent(argThat(events -> events.size() == 1));
        then(hubRouteEventPublisher).should().publishRouteBuildCompleted(any());
        InOrder order = inOrder(hubRouteRepository);
        order.verify(hubRouteRepository).lockBuildHub(center1.getHubId());
        order.verify(hubRouteRepository).hasIncompleteRoutes(center1.getHubId());
    }

    @Test
    @DisplayName("재시도 한도에 도달한 경로 쌍은 모두 실패 처리하고 허브 실패 이벤트를 발행한다")
    void failRoutePairBuildPublishesFailureWhenRetryIsExhausted() {
        // given
        HubRoute outbound = route(center1, center2);
        HubRoute inbound = route(center2, center1);
        List<UUID> routeIds = List.of(outbound.getRouteId(), inbound.getRouteId());
        given(hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds))
                .willReturn(List.of(outbound, inbound));

        // when
        service.failRoutePairBuild(routeIds, "map down", 1, Duration.ofSeconds(30));

        // then
        assertThat(outbound.getStatus()).isEqualTo(HubRouteStatus.FAILED);
        assertThat(inbound.getStatus()).isEqualTo(HubRouteStatus.FAILED);
        then(hubRouteRepository).should().saveAll(List.of(outbound, inbound));
        then(hubRouteEventPublisher).should().publishRouteBuildFailed(center1.getHubId(), "map down");
    }

    @Test
    @DisplayName("대체 공급자로 계산한 경로는 짧은 재조정 시각을 예약한다")
    void completeRoutePairBuildSchedulesFallbackReconciliation() {
        // given
        HubRoute outbound = route(center1, center2);
        HubRoute inbound = route(center2, center1);
        List<UUID> routeIds = List.of(outbound.getRouteId(), inbound.getRouteId());
        given(hubRouteRepository.findAllByIdsWithHubsForUpdate(routeIds))
                .willReturn(List.of(outbound, inbound));
        given(hubRouteRepository.hasIncompleteRoutes(center1.getHubId())).willReturn(true);
        LocalDateTime before = LocalDateTime.now().plusMinutes(1).minusSeconds(1);

        // when
        service.completeRoutePairBuild(routeIds, weight(), RouteProvider.NAVER, true);

        // then
        LocalDateTime after = LocalDateTime.now().plusMinutes(1).plusSeconds(1);
        assertThat(outbound.getNextRefreshAt()).isBetween(before, after);
        assertThat(inbound.getNextRefreshAt()).isEqualTo(outbound.getNextRefreshAt());
        assertThat(outbound.getResolvedProvider()).isEqualTo(RouteProvider.NAVER);
        assertThat(outbound.getResolvedByFallback()).isTrue();
        then(hubRouteEventPublisher).should(never()).publishRouteBuildCompleted(any());
    }

    private HubRoute route(Hub start, Hub end) {
        return HubRoute.skeleton(center1.getHubId(), start, end);
    }

    private RouteWeight weight() {
        return RouteWeight.of(BigDecimal.valueOf(12.3), 25);
    }

    private Hub hub(String name, double latitude, double longitude) {
        return Hub.builder()
                .hubId(UUID.randomUUID())
                .name(name)
                .hubType(HubType.CENTER)
                .address(Address.of("주소", Coordinate.of(latitude, longitude)))
                .centerHubRelations(new HashSet<>())
                .branchHubRelations(new HashSet<>())
                .build();
    }
}
