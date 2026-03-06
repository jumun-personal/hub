package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.hub.hub.application.command.CreateHubCommand;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepositoryCustom;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteBuildJobService;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class HubServiceImplRoutePreparationTest {

    @Mock
    private HubRepository hubRepository;
    @Mock
    private HubRepositoryCustom hubRepositoryCustom;
    @Mock
    private HubEventPublisher hubEventPublisher;
    @Mock
    private HubRouteService hubRouteService;
    @Mock
    private HubRouteBuildJobService hubRouteBuildJobService;

    @Test
    @DisplayName("센터 허브 생성은 요청 트랜잭션에서 경로 skeleton과 Route Build Job을 저장한다")
    void createCenter_preparesRouteSkeletonBeforePublishingHubCreatedEvent() {
        // given
        UUID hubId = UUID.randomUUID();
        HubServiceImpl service = new HubServiceImpl(
                hubRepository,
                hubRepositoryCustom,
                hubEventPublisher,
                hubRouteService,
                hubRouteBuildJobService
        );
        given(hubRepository.save(any(Hub.class))).willAnswer(invocation -> {
            Hub hub = invocation.getArgument(0);
            ReflectionTestUtils.setField(hub, "hubId", hubId);
            return hub;
        });
        given(hubRouteService.prepareRoutesForBuildJob(hubId)).willReturn(3);

        // when
        service.create(CreateHubCommand.createCenter("서울 센터", "서울", 37.5, 127.0, HubType.CENTER));

        // then
        then(hubRepository).should().flush();
        then(hubRouteBuildJobService).should().lockTopology();
        then(hubRouteService).should().prepareRoutesForBuildJob(hubId);
        then(hubRouteBuildJobService).should().request(any(Hub.class), eq(3));
        then(hubEventPublisher).should(never()).publishEvent(any(HubCreatedEvent.class));
    }

    @Test
    @DisplayName("연결할 경로가 없는 첫 센터 허브는 요청 트랜잭션에서 즉시 완료한다")
    void createCenter_whenNoRoutePair_completesImmediately() {
        // given
        UUID hubId = UUID.randomUUID();
        HubServiceImpl service = new HubServiceImpl(
                hubRepository,
                hubRepositoryCustom,
                hubEventPublisher,
                hubRouteService,
                hubRouteBuildJobService
        );
        given(hubRepository.save(any(Hub.class))).willAnswer(invocation -> {
            Hub hub = invocation.getArgument(0);
            ReflectionTestUtils.setField(hub, "hubId", hubId);
            return hub;
        });
        given(hubRouteService.prepareRoutesForBuildJob(hubId)).willReturn(0);

        // when
        service.create(CreateHubCommand.createCenter("첫 센터", "서울", 37.5, 127.0, HubType.CENTER));

        // then
        then(hubRouteBuildJobService).should().request(any(Hub.class), eq(0));
        then(hubEventPublisher).should().publishEvent(any(HubCreatedEvent.class));
    }

    @Test
    @DisplayName("경로 skeleton 저장에 실패하면 Job과 완료 이벤트를 남기지 않는다")
    void createCenter_whenSkeletonPreparationFails_doesNotCreateJobOrPublishEvent() {
        // given
        UUID hubId = UUID.randomUUID();
        HubServiceImpl service = new HubServiceImpl(
                hubRepository,
                hubRepositoryCustom,
                hubEventPublisher,
                hubRouteService,
                hubRouteBuildJobService
        );
        given(hubRepository.save(any(Hub.class))).willAnswer(invocation -> {
            Hub hub = invocation.getArgument(0);
            ReflectionTestUtils.setField(hub, "hubId", hubId);
            return hub;
        });
        given(hubRouteService.prepareRoutesForBuildJob(hubId))
                .willThrow(new IllegalStateException("skeleton insert failed"));

        // when & then
        assertThatThrownBy(() -> service.create(
                CreateHubCommand.createCenter("서울 센터", "서울", 37.5, 127.0, HubType.CENTER)
        )).isInstanceOf(IllegalStateException.class);
        then(hubRouteBuildJobService).should(never()).request(any(Hub.class), any(Integer.class));
        then(hubEventPublisher).should(never()).publishEvent(any(HubCreatedEvent.class));
    }
}
