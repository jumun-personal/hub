package com.jumunhasyeo.hubRoute.infrastructure.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubStatus;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import com.jumunhasyeo.hub.hubRoute.infrastructure.repository.JpaHubRouteRepositoryImpl;
import com.jumunhasyeo.hub.hubRoute.infrastructure.repository.AdapterHubRouteRepository;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import com.jumunhasyeo.testsupport.RepositorySliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(AdapterHubRouteRepository.class)
class JpaHubRouteRepositorySliceTest extends RepositorySliceTest {

    @Autowired
    private JpaHubRouteRepositoryImpl hubRouteRepository;

    @Autowired
    private HubRouteRepository hubRouteRepositoryAdapter;
    @Test
    @DisplayName("시작 허브 또는 종료 허브로 경로를 조회할 수 있다.")
    void findByStartHubOrEndHub_success() {
        //given
        Hub hub1 = createHub("서울");
        Hub hub2 = createHub("부산");
        Hub hub3 = createHub("대전");
        testEntityManager.persist(hub1);
        testEntityManager.persist(hub2);
        testEntityManager.persist(hub3);
        testEntityManager.persist(createRoute(hub1, hub2));
        testEntityManager.persist(createRoute(hub2, hub3));
        testEntityManager.flush();

        //when
        List<HubRoute> routes = hubRouteRepository.findByStartHubOrEndHub(hub1, hub1);

        //then
        assertThat(routes).hasSize(1);
    }

    @Test
    @DisplayName("삭제된 경로는 조회되지 않는다.")
    void findByStartHubOrEndHub_excludesDeleted() {
        //given
        Hub hub1 = createHub("서울");
        Hub hub2 = createHub("부산");
        testEntityManager.persist(hub1);
        testEntityManager.persist(hub2);
        HubRoute route = createRoute(hub1, hub2);
        route.markDeleted(1L);
        testEntityManager.persist(route);
        testEntityManager.flush();

        //when
        List<HubRoute> routes = hubRouteRepository.findByStartHubOrEndHub(hub1, hub1);

        //then
        assertThat(routes).isEmpty();
    }

    @Test
    @DisplayName("모든 경로를 조회할 수 있다.")
    void findAll_success() {
        //given
        Hub hub1 = createHub("서울");
        Hub hub2 = createHub("부산");
        testEntityManager.persist(hub1);
        testEntityManager.persist(hub2);
        testEntityManager.persist(createRoute(hub1, hub2));
        testEntityManager.persist(createRoute(hub2, hub1));
        testEntityManager.flush();

        //when
        List<HubRoute> routes = hubRouteRepository.findAll();

        //then
        assertThat(routes).hasSize(2);
    }

    @Test
    @DisplayName("insertIgnore로 경로를 저장할 수 있다.")
    void insertIgnore_success() {
        //given
        Hub hub1 = createHub("서울");
        Hub hub2 = createHub("부산");
        testEntityManager.persist(hub1);
        testEntityManager.persist(hub2);
        testEntityManager.flush();

        //when
        hubRouteRepository.insertIgnore(hub1.getHubId(), hub2.getHubId(), 300.0, 180);
        testEntityManager.clear();

        //then
        List<HubRoute> routes = hubRouteRepository.findAll();
        assertThat(routes).hasSize(1);
    }

    @Test
    @DisplayName("insertIgnore로 중복 경로는 무시된다.")
    void insertIgnore_duplicateIgnored() {
        //given
        Hub hub1 = createHub("서울");
        Hub hub2 = createHub("부산");
        testEntityManager.persist(hub1);
        testEntityManager.persist(hub2);
        testEntityManager.flush();

        //when
        hubRouteRepository.insertIgnore(hub1.getHubId(), hub2.getHubId(), 300.0, 180);
        hubRouteRepository.insertIgnore(hub1.getHubId(), hub2.getHubId(), 400.0, 200);
        testEntityManager.clear();

        //then
        List<HubRoute> routes = hubRouteRepository.findAll();
        assertThat(routes).hasSize(1);
    }

    @Test
    @DisplayName("JDBC Bulk Insert는 양방향 경로를 한 배치로 저장하고 생성된 ID를 반환한다.")
    void bulkInsertIgnore_savesBidirectionalRoutes() {
        // given
        Hub hub1 = createHub("서울");
        Hub hub2 = createHub("부산");
        testEntityManager.persist(hub1);
        testEntityManager.persist(hub2);
        testEntityManager.flush();
        UUID buildHubId = UUID.randomUUID();
        Set<HubRoute> routes = HubRoute.createTwoWaySkeleton(buildHubId, hub1, hub2);

        // when
        Set<UUID> insertedRouteIds = hubRouteRepositoryAdapter.insertIgnore(routes);
        testEntityManager.clear();

        // then
        assertThat(insertedRouteIds).containsExactlyInAnyOrderElementsOf(
                routes.stream().map(HubRoute::getRouteId).toList()
        );
        assertThat(hubRouteRepository.findAllById(insertedRouteIds)).hasSize(2);
    }

    @Test
    @DisplayName("JDBC Bulk Insert는 동일한 활성 경로의 중복 저장을 무시한다.")
    void bulkInsertIgnore_ignoresDuplicates() {
        // given
        Hub hub1 = createHub("서울");
        Hub hub2 = createHub("부산");
        testEntityManager.persist(hub1);
        testEntityManager.persist(hub2);
        testEntityManager.flush();
        Set<HubRoute> firstRoutes = HubRoute.createTwoWaySkeleton(UUID.randomUUID(), hub1, hub2);
        Set<HubRoute> duplicateRoutes = HubRoute.createTwoWaySkeleton(UUID.randomUUID(), hub1, hub2);
        hubRouteRepositoryAdapter.insertIgnore(firstRoutes);

        // when
        Set<UUID> insertedRouteIds = hubRouteRepositoryAdapter.insertIgnore(duplicateRoutes);
        testEntityManager.clear();

        // then
        assertThat(insertedRouteIds).isEmpty();
        assertThat(hubRouteRepository.findAllById(
                firstRoutes.stream().map(HubRoute::getRouteId).toList()
        )).hasSize(2);
    }

    private Hub createHub(String name) {
        return Hub.builder()
                .name(name)
                .hubType(HubType.CENTER)
                .status(HubStatus.COMPLETE)
                .address(Address.of("주소", Coordinate.of(37.5, 127.0)))
                .build();
    }

    private HubRoute createRoute(Hub start, Hub end) {
        return HubRoute.builder()
                .startHub(start)
                .endHub(end)
                .status(HubRouteStatus.COMPLETE)
                .routeWeight(RouteWeight.of(BigDecimal.valueOf(100), 60))
                .build();
    }
}
