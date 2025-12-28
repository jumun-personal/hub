package com.jumunhasyeo.hubRoute.domain.service;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.domain.service.HubRouteDomainService;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class HubRouteDomainServiceTest {

    private HubRouteDomainService domainService;
    private Hub centerHub1;
    private Hub centerHub2;
    private Hub branchHub;

    @BeforeEach
    void setUp() {
        domainService = new HubRouteDomainService();

        centerHub1 = Hub.builder()
                .hubId(UUID.randomUUID())
                .name("대전센터")
                .branchHubRelations(new HashSet<>())
                .centerHubRelations(new HashSet<>())
                .address(Address.of("대전 서구", Coordinate.of(36.3505, 127.3845)))
                .hubType(HubType.CENTER)
                .build();

        centerHub2 = Hub.builder()
                .hubId(UUID.randomUUID())
                .name("대구센터")
                .branchHubRelations(new HashSet<>())
                .centerHubRelations(new HashSet<>())
                .address(Address.of("대전 서구", Coordinate.of(36.3505, 127.3845)))
                .hubType(HubType.CENTER)
                .build();

        branchHub = Hub.builder()
                .hubId(UUID.randomUUID())
                .branchHubRelations(new HashSet<>())
                .centerHubRelations(new HashSet<>())
                .name("서울지점")
                .address(Address.of("서울 송파구", Coordinate.of(37.5146, 127.1061)))
                .hubType(HubType.BRANCH)
                .build();

        branchHub.addCenterHub(centerHub1);
    }

    @Test
    @DisplayName("센터 허브 경로 생성 - 모든 센터와 연결")
    void buildRoutesForNewCenterHub_Success() {
        // given
        List<Hub> existingCenters = List.of(centerHub2);
        HubRouteDomainService.RouteWeightCalculator calculator = 
            (from, to) -> RouteWeight.of(BigDecimal.valueOf(150.5), 120);

        // when
        Set<HubRoute> routes = domainService.buildRoutesForNewCenterHub(
            centerHub1,
            existingCenters,
            calculator
        );

        // then
        assertThat(routes).hasSize(2); // 양방향
        assertThat(routes).allMatch(route -> 
            route.getRouteWeight().getDistanceKm().doubleValue() == 150.5
        );
    }

    @Test
    @DisplayName("지점 허브 경로 생성 - 소속 센터와만 연결")
    void buildRoutesForNewBranchHub_Success() {
        // given
        HubRouteDomainService.RouteWeightCalculator calculator = 
            (from, to) -> RouteWeight.of(BigDecimal.valueOf(56.79), 66);

        // when
        Set<HubRoute> routes = domainService.buildRoutesForNewBranchHub(
            branchHub,
            centerHub1,
            calculator
        );

        // then
        assertThat(routes).hasSize(2); // 양방향
    }

    @Test
    @DisplayName("경로 생성 검증 - 같은 Hub끼리는 실패")
    void validateRouteCreation_SameHub_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> 
            domainService.validateRouteCreation(centerHub1, centerHub1)
        )
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CANNOT_CREATE_ROUTE_SAME_HUB);
    }

    @Test
    @DisplayName("경로 생성 검증 - 삭제된 Hub는 실패")
    void validateRouteCreation_DeletedHub_ThrowsException() {
        // given
        Hub hub = Hub.builder()
                .hubId(UUID.randomUUID())
                .name("대전센터")
                .branchHubRelations(new HashSet<>())
                .centerHubRelations(new HashSet<>())
                .address(Address.of("대전 서구", Coordinate.of(36.3505, 127.3845)))
                .hubType(HubType.CENTER)
                .build();
        hub.delete(1L);

        // when & then
        assertThatThrownBy(() -> 
            domainService.validateRouteCreation(hub, centerHub2)
        )
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CANNOT_CREATE_ROUTE_FROM_DELETED_HUB);
    }

    @Test
    @DisplayName("경로 생성 검증 - 도착 Hub가 삭제된 경우 실패")
    void validateRouteCreation_DeletedToHub_ThrowsException() {
        Hub deletedToHub = Hub.builder()
                .hubId(UUID.randomUUID())
                .name("삭제된센터")
                .branchHubRelations(new HashSet<>())
                .centerHubRelations(new HashSet<>())
                .address(Address.of("대전 서구", Coordinate.of(36.3505, 127.3845)))
                .hubType(HubType.CENTER)
                .build();
        deletedToHub.delete(1L);

        assertThatThrownBy(() -> domainService.validateRouteCreation(centerHub1, deletedToHub))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_CREATE_ROUTE_TO_DELETED_HUB);
    }

    @Test
    @DisplayName("경로 생성 검증 - null Hub는 실패")
    void validateRouteCreation_NullHub_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> 
            domainService.validateRouteCreation(null, centerHub1)
        )
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.HUB_CANNOT_BE_NULL);
    }

    @Test
    @DisplayName("지점 허브 생성 시 centerHub가 CENTER 타입이 아니면 실패")
    void buildRoutesForNewBranchHub_centerHubTypeInvalid_throwsException() {
        Hub invalidCenter = Hub.builder()
                .hubId(UUID.randomUUID())
                .branchHubRelations(new HashSet<>())
                .centerHubRelations(new HashSet<>())
                .name("가짜센터")
                .address(Address.of("서울 송파구", Coordinate.of(37.5146, 127.1061)))
                .hubType(HubType.BRANCH)
                .build();

        assertThatThrownBy(() -> domainService.buildRoutesForNewBranchHub(
                branchHub,
                invalidCenter,
                (from, to) -> RouteWeight.of(BigDecimal.valueOf(10), 10)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HUB_MUST_BE_CENTER_TYPE);
    }

    @Test
    @DisplayName("지점 허브 생성 시 centerHub와 연결되지 않았으면 실패")
    void buildRoutesForNewBranchHub_notConnectedToCenter_throwsException() {
        Hub otherCenter = Hub.builder()
                .hubId(UUID.randomUUID())
                .name("타센터")
                .branchHubRelations(new HashSet<>())
                .centerHubRelations(new HashSet<>())
                .address(Address.of("대구 수성구", Coordinate.of(35.8583, 128.6306)))
                .hubType(HubType.CENTER)
                .build();

        assertThatThrownBy(() -> domainService.buildRoutesForNewBranchHub(
                branchHub,
                otherCenter,
                (from, to) -> RouteWeight.of(BigDecimal.valueOf(10), 10)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BRANCH_NOT_CONNECTED_TO_CENTER);
    }
}
