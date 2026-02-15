package com.jumunhasyeo.hub.hubRoute.domain.service;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Hub와 HubRoute 간의 경로 생성 정책을 관리하는 Domain Service
 * 
 * Domain Service인 이유:
 * 1. 여러 Aggregate(Hub, HubRoute)를 넘나드는 도메인 로직
 * 2. "센터는 모든 센터와 연결", "지점은 소속 센터와 연결" 같은 비즈니스 규칙
 * 3. 순수한 도메인 개념이지만 단일 Aggregate에 속할 수 없음
 */
@Service
public class HubRouteDomainService {

    /**
     * 센터 허브 생성 시 연결되어야 할 경로 생성
     * 비즈니스 규칙: 센터 허브는 모든 다른 센터 허브와 양방향 연결된다
     */
    public Set<HubRoute> buildRoutesForNewCenterHub(
            Hub newCenterHub, 
            List<Hub> existingCenterHubs, 
            RouteWeightCalculator calculator) {
        
        validateCenterHub(newCenterHub);

        Set<HubRoute> routes = existingCenterHubs.stream()
                .filter(existingCenter -> !existingCenter.equals(newCenterHub))
                .peek(existingCenter -> validateRouteCreation(newCenterHub, existingCenter))
                .flatMap(existingCenter -> createTwoWayRoutes(existingCenter, newCenterHub, calculator).stream())
                .collect(Collectors.toSet());
        
        return routes;
    }

    /**
     * 지점 허브 생성 시 연결되어야 할 경로 생성
     * 비즈니스 규칙: 지점 허브는 소속된 센터 허브와만 양방향 연결, 같은 소속 지점허브와 연결
     */
    public Set<HubRoute> buildRoutesForNewBranchHub(
            Hub branchHub, 
            Hub centerHub, 
            RouteWeightCalculator calculator) {
        
        validateBranchHub(branchHub);
        validateCenterHub(centerHub);
        validateBranchExistToCenter(branchHub, centerHub);
        validateRouteCreation(branchHub, centerHub);

        HashSet<HubRoute> branchToCenter = createTwoWayRoutes(branchHub, centerHub, calculator);
        HashSet<HubRoute> branchToBranch = centerHub.getBranchHubs()
                .stream()
                .filter(relBranchHub -> !relBranchHub.equals(branchHub))
                .flatMap(filtered -> createTwoWayRoutes(branchHub, filtered, calculator).stream())
                .collect(Collectors.toCollection(HashSet::new));

        return merge(branchToCenter, branchToBranch);
    }

    /**
     * 센터 허브 생성 시 API 호출 전 skeleton 경로만 생성한다.
     */
    public Set<HubRoute> buildRouteSkeletonsForNewCenterHub(
            UUID buildHubId,
            Hub newCenterHub,
            List<Hub> existingCenterHubs) {

        validateCenterHub(newCenterHub);

        return existingCenterHubs.stream()
                .filter(existingCenter -> !existingCenter.equals(newCenterHub))
                .peek(existingCenter -> validateRouteCreation(newCenterHub, existingCenter))
                .flatMap(existingCenter -> createTwoWayRouteSkeletons(buildHubId, existingCenter, newCenterHub).stream())
                .collect(Collectors.toSet());
    }

    /**
     * 지점 허브 생성 시 API 호출 전 skeleton 경로만 생성한다.
     */
    public Set<HubRoute> buildRouteSkeletonsForNewBranchHub(
            UUID buildHubId,
            Hub branchHub,
            Hub centerHub) {

        validateBranchHub(branchHub);
        validateCenterHub(centerHub);
        validateBranchExistToCenter(branchHub, centerHub);
        validateRouteCreation(branchHub, centerHub);

        HashSet<HubRoute> branchToCenter = createTwoWayRouteSkeletons(buildHubId, branchHub, centerHub);
        HashSet<HubRoute> branchToBranch = centerHub.getBranchHubs()
                .stream()
                .filter(relBranchHub -> !relBranchHub.equals(branchHub))
                .flatMap(filtered -> createTwoWayRouteSkeletons(buildHubId, branchHub, filtered).stream())
                .collect(Collectors.toCollection(HashSet::new));

        return merge(branchToCenter, branchToBranch);
    }

    private Set<HubRoute> merge(HashSet<HubRoute> branchToCenter, HashSet<HubRoute> branchToBranch) {
        HashSet<HubRoute> merged = new HashSet<>();
        merged.addAll(branchToCenter);
        merged.addAll(branchToBranch);
        return merged;
    }

    private HashSet<HubRoute> createTwoWayRoutes(Hub branchHub, Hub centerHub, RouteWeightCalculator calculator) {
        RouteWeight weight = calculator.calculate(branchHub, centerHub);
        return HubRoute.createTwoWay(branchHub, centerHub, weight);
    }

    private HashSet<HubRoute> createTwoWayRouteSkeletons(UUID buildHubId, Hub from, Hub to) {
        return HubRoute.createTwoWaySkeleton(buildHubId, from, to);
    }

    /**
     * 경로 생성이 가능한지 검증
     * 
     * 비즈니스 규칙:
     * - 삭제된 Hub 간에는 경로 생성 불가
     * - 같은 Hub끼리는 경로 생성 불가
     * - null Hub는 허용 안됨
     */
    public void validateRouteCreation(Hub from, Hub to) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.HUB_CANNOT_BE_NULL);
        }
        
        if (from.getHubId().equals(to.getHubId())) {
            throw new BusinessException(ErrorCode.CANNOT_CREATE_ROUTE_SAME_HUB, 
                String.format("Hub: %s", from.getName()));
        }
        
        if (from.isDeleted()) {
            throw new BusinessException(ErrorCode.CANNOT_CREATE_ROUTE_FROM_DELETED_HUB,
                String.format("Hub: %s", from.getName()));
        }
        
        if (to.isDeleted()) {
            throw new BusinessException(ErrorCode.CANNOT_CREATE_ROUTE_TO_DELETED_HUB,
                String.format("Hub: %s", to.getName()));
        }
    }

    private void validateCenterHub(Hub hub) {
        if (!hub.isCenterHub()) {
            throw new BusinessException(ErrorCode.HUB_MUST_BE_CENTER_TYPE,
                String.format("Hub: %s, Type: %s", hub.getName(), hub.getHubType()));
        }
    }

    private void validateBranchHub(Hub hub) {
        if (!hub.isBranchHub()) {
            throw new BusinessException(ErrorCode.HUB_MUST_BE_BRANCH_TYPE,
                String.format("Hub: %s, Type: %s", hub.getName(), hub.getHubType()));
        }
    }

    private void validateBranchExistToCenter(Hub branchHub, Hub centerHub) {
        boolean isConnected = branchHub.getCenterHubs().contains(centerHub);
        if (!isConnected) {
            throw new BusinessException(ErrorCode.BRANCH_NOT_CONNECTED_TO_CENTER,
                String.format("Branch: %s, Center: %s", branchHub.getName(), centerHub.getName()));
        }
    }

    /**
     * 경로 가중치(거리, 시간) 계산을 추상화한 인터페이스
     * Application Layer에서 실제 구현체(외부 API 호출)를 주입
     */
    @FunctionalInterface
    public interface RouteWeightCalculator {
        RouteWeight calculate(Hub from, Hub to);
    }
}
