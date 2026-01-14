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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HubRouteServiceTest {

    @Mock
    private HubRepository hubRepository;
    @Mock
    private RouteWeightApiService routeWeightApi;
    @Mock
    private HubRouteRepository hubRouteRepository;
    @Mock
    private HubRouteDomainService hubRouteDomainService;
    @Mock
    private HubRouteEventPublisher hubRouteEventPublisher;

    @InjectMocks
    private HubRouteService hubRouteService;

    private Hub center1;
    private Hub center2;
    private Hub branch;

    @BeforeEach
    void setUp() {
        center1 = hub(UUID.randomUUID(), "센터1", HubType.CENTER, 37.5, 127.0);
        center2 = hub(UUID.randomUUID(), "센터2", HubType.CENTER, 35.8, 128.6);
        branch = hub(UUID.randomUUID(), "지점1", HubType.BRANCH, 37.4, 127.1);
        branch.addCenterHub(center1);
    }

    @Test
    @DisplayName("CENTER 허브 생성 시 경로를 생성하고 이벤트를 발행한다.")
    void build_routes_for_center_success() {
        BuildRouteCommand command = new BuildRouteCommand(null, center1.getHubId(), center1.getName(), center1.getAddress(), HubType.CENTER);
        when(hubRepository.findByIdIncludingCreating(center1.getHubId())).thenReturn(Optional.of(center1));
        when(hubRepository.findAllByHubType(HubType.CENTER)).thenReturn(List.of(center1, center2));

        Set<HubRoute> routes = HubRoute.createTwoWay(center2, center1, RouteWeight.of(BigDecimal.valueOf(11.9), 32));
        when(hubRouteDomainService.buildRoutesForNewCenterHub(any(), any(), any())).thenReturn(routes);

        hubRouteService.buildRoutesForNewHub(command);

        verify(hubRouteRepository).insertIgnore(routes);
        ArgumentCaptor<List<HubRouteCreatedEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(hubRouteEventPublisher).publishRouteCreatedEvent(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("BRANCH 허브 생성 시 경로를 생성하고 이벤트를 발행한다.")
    void build_routes_for_branch_success() {
        BuildRouteCommand command = new BuildRouteCommand(center1.getHubId(), branch.getHubId(), branch.getName(), branch.getAddress(), HubType.BRANCH);
        when(hubRepository.findByIdIncludingCreating(branch.getHubId())).thenReturn(Optional.of(branch));
        when(hubRepository.findById(center1.getHubId())).thenReturn(Optional.of(center1));

        Set<HubRoute> routes = HubRoute.createTwoWay(branch, center1, RouteWeight.of(BigDecimal.valueOf(7.2), 19));
        when(hubRouteDomainService.buildRoutesForNewBranchHub(any(), any(), any())).thenReturn(routes);

        hubRouteService.buildRoutesForNewHub(command);

        verify(hubRouteRepository).insertIgnore(routes);
        verify(hubRouteEventPublisher).publishRouteCreatedEvent(any());
    }

    @Test
    @DisplayName("허브가 없으면 실패 이벤트를 발행한다.")
    void build_routes_hub_not_found() {
        BuildRouteCommand command = new BuildRouteCommand(null, UUID.randomUUID(), "센터", center1.getAddress(), HubType.CENTER);
        when(hubRepository.findByIdIncludingCreating(command.hubId())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> hubRouteService.buildRoutesForNewHub(command));

        verify(hubRouteEventPublisher, never()).publishRouteBuildFailed(any(), any());
        verify(hubRouteEventPublisher, never()).publishRouteCreatedEvent(any());
        verify(hubRouteRepository, never()).insertIgnore(any(Set.class));
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
}
