package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.hub.infrastructure.outbox.JpaOutboxRepository;
import com.jumunhasyeo.hub.infrastructure.outbox.OutboxEvent;
import com.jumunhasyeo.hub.hub.application.HubCreationSagaService;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubStatus;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hub.infrastructure.repository.JpaHubRepository;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteBuildJobStatus;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteStatus;
import com.jumunhasyeo.hub.hubRoute.domain.entity.RouteProvider;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteBuildJobRepository.HubRouteBuildJobClaim;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import com.jumunhasyeo.hub.hubRoute.infrastructure.repository.JpaHubRouteBuildJobRepository;
import com.jumunhasyeo.hub.hubRoute.infrastructure.repository.JpaHubRouteRepositoryImpl;
import com.jumunhasyeo.testsupport.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HubRouteBuildRetryIntegrationTest extends IntegrationTest {

    @Autowired
    private HubRoutePlanningService planningService;

    @Autowired
    private HubRouteBuildJobService buildJobService;

    @Autowired
    private HubCreationSagaService hubCreationSagaService;

    @Autowired
    private RoutePairLifecycleService routePairLifecycleService;

    @Autowired
    private JpaHubRepository hubRepository;

    @Autowired
    private JpaHubRouteRepositoryImpl routeRepository;

    @Autowired
    private JpaHubRouteBuildJobRepository buildJobRepository;

    @Autowired
    private JpaOutboxRepository outboxRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("전체 경로 구축 실패 후 수동 재시도는 Hub와 Job을 복구하고 실패 Route의 재시도 횟수를 초기화한다")
    void retryFailed_restoresHubJobAndFailedRoutes() {
        // given
        Hub buildHub = saveHub("구축 대상 허브", 37.5, 127.1);
        Hub targetHub = saveHub("연결 대상 허브", 35.2, 129.1);
        Hub completedTargetHub = saveHub("완료 경로 대상 허브", 36.3, 127.4);
        HubRoute failedForward = failedRoute(buildHub, buildHub, targetHub, 3);
        HubRoute failedBackward = failedRoute(buildHub, targetHub, buildHub, 3);
        HubRoute completeRoute = completeRoute(buildHub, buildHub, completedTargetHub);
        routeRepository.saveAll(List.of(failedForward, failedBackward, completeRoute));

        requestBuildJob(buildHub);
        HubRouteBuildJobClaim claim = buildJobService.claimPlanning(LocalDateTime.now().minusMinutes(5)).orElseThrow();
        buildJobService.initialize(buildHub.getHubId(), claim.processingToken(), 1);
        var counter = buildJobService.failPair(buildHub.getHubId(), "map provider exhausted").orElseThrow();
        planningService.finalizeIfTerminal(counter, "map provider exhausted");
        clearPersistenceContext();

        assertThat(hubRepository.findByIdIncludingCreating(buildHub.getHubId()).orElseThrow())
                .satisfies(hub -> {
                    assertThat(hub.getStatus()).isEqualTo(HubStatus.FAILED);
                    assertThat(hub.isDeleted()).isFalse();
                });

        // when
        planningService.retryFailed(buildHub.getHubId());
        clearPersistenceContext();

        // then
        assertThat(hubRepository.findByIdIncludingCreating(buildHub.getHubId()).orElseThrow().getStatus())
                .isEqualTo(HubStatus.PENDING);
        assertThat(buildJobRepository.findById(buildHub.getHubId()).orElseThrow())
                .satisfies(job -> {
                    assertThat(job.getStatus()).isEqualTo(HubRouteBuildJobStatus.RUNNING);
                    assertThat(job.getRemainingCount()).isEqualTo(1);
                    assertThat(job.getFailedCount()).isZero();
                    assertThat(job.getRetryCount()).isEqualTo(1);
                });
        assertThat(routeRepository.findAllById(List.of(failedForward.getRouteId(), failedBackward.getRouteId())))
                .allSatisfy(route -> {
                    assertThat(route.getStatus()).isEqualTo(HubRouteStatus.PENDING);
                    assertThat(route.getRetryCount()).isZero();
                    assertThat(route.getProcessingToken()).isNull();
                    assertThat(route.getErrorMessage()).isNull();
                    assertThat(route.getNextRetryAt()).isNotNull();
                });
        assertThat(routeRepository.findById(completeRoute.getRouteId()).orElseThrow().getStatus())
                .isEqualTo(HubRouteStatus.COMPLETE);

        assertThatThrownBy(() -> planningService.retryFailed(buildHub.getHubId()))
                .isInstanceOf(BusinessException.class);
        assertThat(buildJobRepository.findById(buildHub.getHubId()).orElseThrow().getRetryCount())
                .isEqualTo(1);

        var target = routePairLifecycleService.claimRoutePairBuild(
                List.of(failedForward.getRouteId(), failedBackward.getRouteId())
        ).orElseThrow();
        routePairLifecycleService.completeRoutePairBuild(
                target.routeIds(),
                target.processingToken(),
                RouteWeight.of(BigDecimal.valueOf(12.4), 25),
                RouteProvider.KAKAO,
                false
        );
        clearPersistenceContext();

        assertThat(hubRepository.findById(buildHub.getHubId(), HubStatus.COMPLETE)).isPresent();
        assertThat(buildJobRepository.findById(buildHub.getHubId()).orElseThrow().getStatus())
                .isEqualTo(HubRouteBuildJobStatus.COMPLETE);
        assertThat(outboxRepository.findAll())
                .extracting(OutboxEvent::getEventName)
                .filteredOn("HubCreatedEvent"::equals)
                .hasSize(1);
    }

    @Test
    @DisplayName("Job 상태가 재시도 조건과 다르면 Hub와 Route의 선행 변경을 모두 롤백한다")
    void retryFailed_whenJobTransitionConflicts_rollsBackHubAndRoutes() {
        // given
        Hub buildHub = saveHub("구축 대상 허브", 37.5, 127.1);
        Hub targetHub = saveHub("연결 대상 허브", 35.2, 129.1);
        HubRoute failedForward = failedRoute(buildHub, buildHub, targetHub, 3);
        HubRoute failedBackward = failedRoute(buildHub, targetHub, buildHub, 3);
        routeRepository.saveAll(List.of(failedForward, failedBackward));
        requestBuildJob(buildHub);
        hubCreationSagaService.failRouteBuild(buildHub.getHubId(), "forced failure");
        clearPersistenceContext();

        // when & then
        assertThatThrownBy(() -> planningService.retryFailed(buildHub.getHubId()))
                .isInstanceOf(BusinessException.class);
        clearPersistenceContext();

        assertThat(hubRepository.findByIdIncludingCreating(buildHub.getHubId()).orElseThrow().getStatus())
                .isEqualTo(HubStatus.FAILED);
        assertThat(routeRepository.findAllById(List.of(failedForward.getRouteId(), failedBackward.getRouteId())))
                .allSatisfy(route -> {
                    assertThat(route.getStatus()).isEqualTo(HubRouteStatus.FAILED);
                    assertThat(route.getRetryCount()).isEqualTo(3);
                });
    }

    private Hub saveHub(String name, double latitude, double longitude) {
        return hubRepository.saveAndFlush(Hub.of(
                name,
                Address.of("테스트 주소 " + name, Coordinate.of(latitude, longitude)),
                HubType.CENTER
        ));
    }

    private HubRoute failedRoute(Hub buildHub, Hub startHub, Hub endHub, int retryCount) {
        return HubRoute.builder()
                .buildHubId(buildHub.getHubId())
                .startHub(startHub)
                .endHub(endHub)
                .status(HubRouteStatus.FAILED)
                .retryCount(retryCount)
                .errorMessage("map provider exhausted")
                .build();
    }

    private HubRoute completeRoute(Hub buildHub, Hub startHub, Hub endHub) {
        return HubRoute.builder()
                .buildHubId(buildHub.getHubId())
                .startHub(startHub)
                .endHub(endHub)
                .status(HubRouteStatus.COMPLETE)
                .retryCount(0)
                .build();
    }

    private void requestBuildJob(Hub hub) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Hub managedHub = hubRepository.findByIdIncludingCreating(hub.getHubId()).orElseThrow();
            buildJobService.request(managedHub);
        });
    }

    private void clearPersistenceContext() {
        entityManager.clear();
    }
}
