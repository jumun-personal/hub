package com.jumunhasyeo.hubRoute.infrastructure.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import com.jumunhasyeo.hub.hubRoute.infrastructure.repository.JpaHubRouteRepositoryImpl;
import com.jumunhasyeo.testsupport.AbstractJpaRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JpaHubRouteRepositoryTest extends AbstractJpaRepositoryTest {

    @Autowired
    private JpaHubRouteRepositoryImpl hubRouteRepository;
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

    private Hub createHub(String name) {
        return Hub.builder()
                .name(name)
                .hubType(HubType.CENTER)
                .address(Address.of("주소", Coordinate.of(37.5, 127.0)))
                .build();
    }

    private HubRoute createRoute(Hub start, Hub end) {
        return HubRoute.builder()
                .startHub(start)
                .endHub(end)
                .routeWeight(RouteWeight.of(BigDecimal.valueOf(100), 60))
                .build();
    }
}
