package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.application.HubRouteEventPublisher;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.HubRouteRes;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteCreatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDeletedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import com.jumunhasyeo.hub.hubRoute.domain.service.HubRouteDomainService;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HubRouteServiceTest {

    @Mock
    private HubRepository hubRepository;
    @Mock
    private HubRouteRepository hubRouteRepository;
    @Mock
    private HubRouteEventPublisher hubRouteEventPublisher;
    private HubRouteService hubRouteService;
    private HubRouteDomainService hubRouteDomainService;

    private Hub center1;
    private Hub center2;
    private Hub branch;

    @BeforeEach
    void setUp() {
        hubRouteDomainService = new HubRouteDomainService();
        hubRouteService = new HubRouteService(
                hubRepository,
                hubRouteRepository,
                hubRouteDomainService,
                hubRouteEventPublisher
        );
        center1 = hub(UUID.randomUUID(), "센터1", HubType.CENTER, 37.5, 127.0);
        center2 = hub(UUID.randomUUID(), "센터2", HubType.CENTER, 35.8, 128.6);
        branch = hub(UUID.randomUUID(), "지점1", HubType.BRANCH, 37.4, 127.1);
        branch.addCenterHub(center1);
        org.mockito.Mockito.lenient().when(hubRouteRepository.insertIgnore(any(Set.class))).thenAnswer(invocation -> {
            Set<HubRoute> routes = invocation.getArgument(0);
            return routes.stream().map(HubRoute::getRouteId).collect(java.util.stream.Collectors.toSet());
        });
    }

    @Test
    @DisplayName("CENTER 허브 생성 시 외부 API 호출 없이 PENDING 경로 skeleton만 저장한다.")
    void build_routes_for_center_success() {
        BuildRouteCommand command = new BuildRouteCommand(null, center1.getHubId(), center1.getName(), center1.getAddress(), HubType.CENTER);
        when(hubRepository.findByIdIncludingCreating(center1.getHubId())).thenReturn(Optional.of(center1));
        when(hubRepository.findAllByHubType(HubType.CENTER)).thenReturn(List.of(center1, center2));
        when(hubRouteRepository.findByStartHubOrEndHub(center1, center1)).thenReturn(List.of());

        hubRouteService.buildRoutesForNewHub(command);

        verify(hubRouteRepository).insertIgnore(argThat(hasRouteCount(2)));
        verify(hubRouteEventPublisher).publishRouteBuildRequested(argThat(events ->
                events.size() == 1 && events.get(0).getRouteIds().size() == 2
        ));
        verify(hubRouteEventPublisher, never()).publishRouteCreatedEvent(any());
        verify(hubRouteEventPublisher, never()).publishRouteBuildCompleted(any());
    }

    @Test
    @DisplayName("BRANCH 허브 생성 시 외부 API 호출 없이 PENDING 경로 skeleton만 저장한다.")
    void build_routes_for_branch_success() {
        BuildRouteCommand command = new BuildRouteCommand(center1.getHubId(), branch.getHubId(), branch.getName(), branch.getAddress(), HubType.BRANCH);
        when(hubRepository.findByIdIncludingCreating(branch.getHubId())).thenReturn(Optional.of(branch));
        when(hubRepository.findById(center1.getHubId())).thenReturn(Optional.of(center1));
        when(hubRouteRepository.findByStartHubOrEndHub(branch, branch)).thenReturn(List.of());

        hubRouteService.buildRoutesForNewHub(command);

        verify(hubRouteRepository).insertIgnore(argThat(hasRouteCount(2)));
        verify(hubRouteEventPublisher, never()).publishRouteCreatedEvent(any());
        verify(hubRouteEventPublisher, never()).publishRouteBuildCompleted(any());
    }

    @Test
    @DisplayName("CENTER 허브 생성 시 기존 양방향 경로가 모두 있으면 외부 API와 created 이벤트를 생략한다.")
    void build_routes_for_center_skipsExistingTwoWayRoutes() {
        BuildRouteCommand command = new BuildRouteCommand(null, center1.getHubId(), center1.getName(), center1.getAddress(), HubType.CENTER);
        when(hubRepository.findByIdIncludingCreating(center1.getHubId())).thenReturn(Optional.of(center1));
        when(hubRepository.findAllByHubType(HubType.CENTER)).thenReturn(List.of(center1, center2));
        when(hubRouteRepository.findByStartHubOrEndHub(center1, center1))
                .thenReturn(List.of(
                        HubRoute.ofSelfId(UUID.randomUUID(), center1, center2, RouteWeight.of(BigDecimal.valueOf(11.9), 32)),
                        HubRoute.ofSelfId(UUID.randomUUID(), center2, center1, RouteWeight.of(BigDecimal.valueOf(11.9), 32))
                ));

        hubRouteService.buildRoutesForNewHub(command);

        verify(hubRouteRepository).insertIgnore(argThat(hasRouteCount(0)));
        verify(hubRouteEventPublisher, never()).publishRouteCreatedEvent(any());
        verify(hubRouteEventPublisher).publishRouteBuildCompleted(command);
    }

    @Test
    @DisplayName("중복 HubCreatedEvent 수신 시 기존 PENDING 경로가 있으면 완료 이벤트를 발행하지 않는다")
    void build_routes_for_center_whenPendingSkeletonExists_skipsBuildCompletedEvent() {
        BuildRouteCommand command = new BuildRouteCommand(null, center1.getHubId(), center1.getName(), center1.getAddress(), HubType.CENTER);
        when(hubRepository.findByIdIncludingCreating(center1.getHubId())).thenReturn(Optional.of(center1));
        when(hubRepository.findAllByHubType(HubType.CENTER)).thenReturn(List.of(center1, center2));
        when(hubRouteRepository.findByStartHubOrEndHub(center1, center1))
                .thenReturn(List.of(
                        HubRoute.skeleton(center1.getHubId(), center1, center2),
                        HubRoute.skeleton(center1.getHubId(), center2, center1)
                ));
        when(hubRouteRepository.hasIncompleteRoutes(center1.getHubId())).thenReturn(true);

        hubRouteService.buildRoutesForNewHub(command);

        verify(hubRouteRepository).insertIgnore(argThat(hasRouteCount(0)));
        verify(hubRouteEventPublisher, never()).publishRouteCreatedEvent(any());
        verify(hubRouteEventPublisher, never()).publishRouteBuildCompleted(any());
    }

    @Test
    @DisplayName("BRANCH 허브 생성 시 기존 양방향 경로가 모두 있으면 외부 API와 created 이벤트를 생략한다.")
    void build_routes_for_branch_skipsExistingTwoWayRoutes() {
        BuildRouteCommand command = new BuildRouteCommand(center1.getHubId(), branch.getHubId(), branch.getName(), branch.getAddress(), HubType.BRANCH);
        when(hubRepository.findByIdIncludingCreating(branch.getHubId())).thenReturn(Optional.of(branch));
        when(hubRepository.findById(center1.getHubId())).thenReturn(Optional.of(center1));
        when(hubRouteRepository.findByStartHubOrEndHub(branch, branch))
                .thenReturn(List.of(
                        HubRoute.ofSelfId(UUID.randomUUID(), branch, center1, RouteWeight.of(BigDecimal.valueOf(7.2), 19)),
                        HubRoute.ofSelfId(UUID.randomUUID(), center1, branch, RouteWeight.of(BigDecimal.valueOf(7.2), 19))
                ));

        hubRouteService.buildRoutesForNewHub(command);

        verify(hubRouteRepository).insertIgnore(argThat(hasRouteCount(0)));
        verify(hubRouteEventPublisher, never()).publishRouteCreatedEvent(any());
        verify(hubRouteEventPublisher).publishRouteBuildCompleted(command);
    }

    @Test
    @DisplayName("CENTER 허브 생성 시 한쪽 방향만 있으면 누락된 방향만 복구한다.")
    void build_routes_for_center_recoversMissingDirectionWithoutApiCall() {
        BuildRouteCommand command = new BuildRouteCommand(null, center1.getHubId(), center1.getName(), center1.getAddress(), HubType.CENTER);
        HubRoute existingRoute = HubRoute.ofSelfId(UUID.randomUUID(), center1, center2, RouteWeight.of(BigDecimal.valueOf(11.9), 32));
        when(hubRepository.findByIdIncludingCreating(center1.getHubId())).thenReturn(Optional.of(center1));
        when(hubRepository.findAllByHubType(HubType.CENTER)).thenReturn(List.of(center1, center2));
        when(hubRouteRepository.findByStartHubOrEndHub(center1, center1)).thenReturn(List.of(existingRoute));

        hubRouteService.buildRoutesForNewHub(command);

        verify(hubRouteRepository).insertIgnore(argThat(routes ->
                routes.size() == 1 && containsRoute(routes, center2, center1)));
        verify(hubRouteEventPublisher, never()).publishRouteCreatedEvent(any());
        verify(hubRouteEventPublisher, never()).publishRouteBuildCompleted(any());
    }

    @Test
    @DisplayName("BRANCH 허브 생성 시 한쪽 방향만 있으면 누락된 방향만 복구한다.")
    void build_routes_for_branch_recoversMissingDirectionWithoutApiCall() {
        BuildRouteCommand command = new BuildRouteCommand(center1.getHubId(), branch.getHubId(), branch.getName(), branch.getAddress(), HubType.BRANCH);
        HubRoute existingRoute = HubRoute.ofSelfId(UUID.randomUUID(), branch, center1, RouteWeight.of(BigDecimal.valueOf(7.2), 19));
        when(hubRepository.findByIdIncludingCreating(branch.getHubId())).thenReturn(Optional.of(branch));
        when(hubRepository.findById(center1.getHubId())).thenReturn(Optional.of(center1));
        when(hubRouteRepository.findByStartHubOrEndHub(branch, branch)).thenReturn(List.of(existingRoute));

        hubRouteService.buildRoutesForNewHub(command);

        verify(hubRouteRepository).insertIgnore(argThat(routes ->
                routes.size() == 1 && containsRoute(routes, center1, branch)));
        verify(hubRouteEventPublisher, never()).publishRouteCreatedEvent(any());
        verify(hubRouteEventPublisher, never()).publishRouteBuildCompleted(any());
    }

    @Test
    @DisplayName("허브가 없으면 예외를 전파하고 실패 이벤트를 직접 발행하지 않는다.")
    void build_routes_hub_not_found() {
        BuildRouteCommand command = new BuildRouteCommand(null, UUID.randomUUID(), "센터", center1.getAddress(), HubType.CENTER);
        when(hubRepository.findByIdIncludingCreating(command.hubId())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> hubRouteService.buildRoutesForNewHub(command));

        verify(hubRouteEventPublisher, never()).publishRouteCreatedEvent(any());
        verify(hubRouteRepository, never()).insertIgnore(any(Set.class));
    }

    @Test
    @DisplayName("스케줄러가 경로 가중치 저장을 완료하면 생성 이벤트와 전체 완료 이벤트를 발행한다.")
    void complete_route_build_publishes_created_and_completed_events() {
        UUID buildHubId = center1.getHubId();
        HubRoute route = HubRoute.skeleton(buildHubId, center1, center2);
        route.claimProcessing();
        when(hubRouteRepository.findByIdWithHubs(route.getRouteId())).thenReturn(Optional.of(route));
        when(hubRouteRepository.hasIncompleteRoutes(buildHubId)).thenReturn(false);
        when(hubRepository.findByIdIncludingCreating(buildHubId)).thenReturn(Optional.of(center1));

        hubRouteService.completeRouteBuild(route.getRouteId(), RouteWeight.of(BigDecimal.valueOf(11.9), 32));

        assertThat(route.isComplete()).isTrue();
        verify(hubRouteRepository).save(route);
        verify(hubRouteEventPublisher).publishRouteCreatedEvent(argThat(events -> events.size() == 1));
        verify(hubRouteEventPublisher).publishRouteBuildCompleted(any(BuildRouteCommand.class));
    }

    @Test
    @DisplayName("경로 가중치 저장이 최종 실패하면 허브 생성 보상을 수행한다.")
    void fail_route_build_compensates_hub_when_retry_exhausted() {
        UUID buildHubId = center1.getHubId();
        HubRoute route = HubRoute.skeleton(buildHubId, center1, center2);
        route.claimProcessing();
        when(hubRouteRepository.findByIdWithHubs(route.getRouteId())).thenReturn(Optional.of(route));

        hubRouteService.failRouteBuild(route.getRouteId(), "map down", 1, Duration.ofSeconds(30));

        verify(hubRouteRepository).save(route);
        verify(hubRouteEventPublisher).publishRouteBuildFailed(buildHubId, "map down");
    }

    @Test
    @DisplayName("삭제 대상 라우트가 없으면 저장/삭제 이벤트를 발행하지 않는다.")
    void delete_routes_no_routes() {
        when(hubRepository.findByIdIncludingDeleted(center1.getHubId())).thenReturn(Optional.of(center1));
        when(hubRouteRepository.findByStartHubOrEndHub(center1, center1)).thenReturn(List.of());

        hubRouteService.deleteRoutesForHub(center1.getHubId(), 1L);

        verify(hubRouteRepository, never()).saveAll(any());
        verify(hubRouteEventPublisher, never()).publishRouteDeletedEvent(any());
    }

    @Test
    @DisplayName("삭제 대상 라우트가 있으면 soft delete 후 이벤트를 발행한다.")
    void delete_routes_success() {
        HubRoute route = HubRoute.ofSelfId(UUID.randomUUID(), center1, center2, RouteWeight.of(BigDecimal.valueOf(10), 20));
        when(hubRepository.findByIdIncludingDeleted(center1.getHubId())).thenReturn(Optional.of(center1));
        when(hubRouteRepository.findByStartHubOrEndHub(center1, center1)).thenReturn(List.of(route));

        hubRouteService.deleteRoutesForHub(center1.getHubId(), 7L);

        assertThat(route.getDeletedBy()).isEqualTo(7L);
        verify(hubRouteRepository).saveAll(List.of(route));
        ArgumentCaptor<List<HubRouteDeletedEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(hubRouteEventPublisher).publishRouteDeletedEvent(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("전체 경로 조회 시 HubRouteRes로 변환한다.")
    void get_all_routes_success() {
        HubRoute route = HubRoute.ofSelfId(UUID.randomUUID(), center1, center2, RouteWeight.of(BigDecimal.valueOf(12.8), 30));
        when(hubRouteRepository.findAll()).thenReturn(List.of(route));

        List<HubRouteRes> result = hubRouteService.getALLRoute();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).routeId()).isEqualTo(route.getRouteId());
        assertThat(result.get(0).distanceKm()).isEqualTo(12);
        assertThat(result.get(0).durationMinutes()).isEqualTo(30);
    }

    private Hub hub(UUID id, String name, HubType type, double lat, double lng) {
        return Hub.builder()
                .hubId(id)
                .name(name)
                .hubType(type)
                .address(Address.of("주소", Coordinate.of(lat, lng)))
                .centerHubRelations(new HashSet<>())
                .branchHubRelations(new HashSet<>())
                .build();
    }

    private ArgumentMatcher<Set<HubRoute>> hasRouteCount(int expectedSize) {
        return routes -> routes != null && routes.size() == expectedSize;
    }

    private boolean containsRoute(Set<HubRoute> routes, Hub startHub, Hub endHub) {
        return routes.stream()
                .anyMatch(route -> route.getStartHub().equals(startHub) && route.getEndHub().equals(endHub));
    }

}
