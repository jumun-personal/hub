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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
    @DisplayName("센터 허브 생성은 동기 경로 준비 없이 Route Build Job을 요청하고 응답한다")
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

        // when
        service.create(CreateHubCommand.createCenter("서울 센터", "서울", 37.5, 127.0, HubType.CENTER));

        // then
        then(hubRepository).should(never()).flush();
        then(hubRouteService).should(never()).buildRoutesForNewHub(any());
        then(hubRouteBuildJobService).should().request(any(Hub.class));
        then(hubEventPublisher).should(never()).publishEvent(any(HubCreatedEvent.class));
    }
}
