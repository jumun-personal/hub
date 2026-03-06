package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hub.application.HubCreationSagaService;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteBuildJobRepository.HubRouteBuildJobClaim;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteBuildJobRepository.HubRouteBuildJobCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class HubRoutePlanningServiceTest {

    @Mock
    private HubRouteBuildJobService buildJobService;

    @Mock
    private HubRouteService hubRouteService;

    @Mock
    private HubCreationSagaService hubCreationSagaService;

    @InjectMocks
    private HubRoutePlanningService planningService;

    @Test
    @DisplayName("생성할 경로가 없는 첫 허브는 Job 초기화 후 즉시 생성 완료한다")
    void initialize_whenNoRoutePair_completesHubImmediately() {
        // given
        UUID hubId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        HubRouteBuildJobClaim claim = new HubRouteBuildJobClaim(hubId, token);
        given(hubRouteService.prepareRoutesForBuildJob(hubId)).willReturn(0);

        // when
        planningService.initialize(claim);

        // then
        InOrder inOrder = inOrder(buildJobService, hubCreationSagaService);
        inOrder.verify(buildJobService).initialize(hubId, token, 0);
        inOrder.verify(hubCreationSagaService).complete(hubId);
    }

    @Test
    @DisplayName("생성할 경로가 있으면 Job만 RUNNING으로 초기화하고 허브 완료를 보류한다")
    void initialize_whenRoutePairsExist_keepsHubPending() {
        // given
        UUID hubId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        HubRouteBuildJobClaim claim = new HubRouteBuildJobClaim(hubId, token);
        given(hubRouteService.prepareRoutesForBuildJob(hubId)).willReturn(100);

        // when
        planningService.initialize(claim);

        // then
        then(buildJobService).should().initialize(hubId, token, 100);
        then(hubCreationSagaService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("남은 경로 쌍이 있으면 최종 완료 판정을 수행하지 않는다")
    void finalizeIfTerminal_whenRemainingCountExists_doesNothing() {
        // given
        UUID hubId = UUID.randomUUID();
        HubRouteBuildJobCounter counter = new HubRouteBuildJobCounter(hubId, 1, 0);

        // when
        planningService.finalizeIfTerminal(counter, "route build failed");

        // then
        then(buildJobService).shouldHaveNoInteractions();
        then(hubRouteService).shouldHaveNoInteractions();
        then(hubCreationSagaService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("영구 실패한 경로 쌍이 있으면 Job과 Hub를 FAILED로 전환한다")
    void finalizeIfTerminal_whenFailedPairExists_failsJobAndHub() {
        // given
        UUID hubId = UUID.randomUUID();
        HubRouteBuildJobCounter counter = new HubRouteBuildJobCounter(hubId, 0, 1);

        // when
        planningService.finalizeIfTerminal(counter, "route build failed");

        // then
        InOrder inOrder = inOrder(buildJobService, hubCreationSagaService);
        inOrder.verify(buildJobService).fail(hubId, "route build failed");
        inOrder.verify(hubCreationSagaService).failRouteBuild(hubId, "route build failed");
    }

    @Test
    @DisplayName("최종 재검사에서 누락 경로를 발견하면 카운터에 추가하고 완료를 보류한다")
    void finalizeIfTerminal_whenMissingPairFound_addsRemainingCount() {
        // given
        UUID hubId = UUID.randomUUID();
        HubRouteBuildJobCounter counter = new HubRouteBuildJobCounter(hubId, 0, 0);
        given(hubRouteService.prepareRoutesForBuildJob(hubId)).willReturn(1);

        // when
        planningService.finalizeIfTerminal(counter, "route build failed");

        // then
        then(buildJobService).should().lockFinalization();
        then(buildJobService).should().addRemaining(hubId, 1);
        then(buildJobService).should(never()).complete(hubId);
        then(hubCreationSagaService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("모든 경로 쌍이 성공하고 누락 경로가 없으면 Job과 허브를 순서대로 완료한다")
    void finalizeIfTerminal_whenAllPairsComplete_completesJobAndHub() {
        // given
        UUID hubId = UUID.randomUUID();
        HubRouteBuildJobCounter counter = new HubRouteBuildJobCounter(hubId, 0, 0);
        given(hubRouteService.prepareRoutesForBuildJob(hubId)).willReturn(0);

        // when
        planningService.finalizeIfTerminal(counter, "route build failed");

        // then
        InOrder inOrder = inOrder(buildJobService, hubRouteService, hubCreationSagaService);
        inOrder.verify(buildJobService).lockFinalization();
        inOrder.verify(hubRouteService).prepareRoutesForBuildJob(hubId);
        inOrder.verify(buildJobService).complete(hubId);
        inOrder.verify(hubCreationSagaService).complete(hubId);
    }

    @Test
    @DisplayName("운영자 재시도는 Hub를 PENDING으로 복구하고 실패 경로와 Job을 다시 실행한다")
    void retryFailed_restoresHubRoutesAndJob() {
        // given
        UUID hubId = UUID.randomUUID();
        given(hubRouteService.resetFailedBuildRoutes(hubId)).willReturn(2);

        // when
        planningService.retryFailed(hubId);

        // then
        InOrder inOrder = inOrder(hubCreationSagaService, hubRouteService, buildJobService);
        inOrder.verify(hubCreationSagaService).retryRouteBuild(hubId);
        inOrder.verify(hubRouteService).resetFailedBuildRoutes(hubId);
        inOrder.verify(buildJobService).retryFailed(hubId, 2);
    }
}
