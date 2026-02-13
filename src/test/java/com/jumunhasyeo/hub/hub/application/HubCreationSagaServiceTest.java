package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.repository.HubRelationRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class HubCreationSagaServiceTest {

    private static final Long SYSTEM_USER_ID = 0L;

    @Mock
    private HubRepository hubRepository;

    @Mock
    private HubRelationRepository hubRelationRepository;

    @Mock
    private HubEventPublisher hubEventPublisher;

    @InjectMocks
    private HubCreationSagaService hubCreationSagaService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(hubCreationSagaService, "deletedBy", SYSTEM_USER_ID);
    }

    @Test
    @DisplayName("허브 경로 생성 완료는 PENDING 상태일 때만 COMPLETE로 전이한다")
    void complete_whenPendingTransitionSucceeded_marksCompleteOnly() {
        // given
        UUID hubId = UUID.randomUUID();
        Hub hub = createHub(hubId);
        given(hubRepository.findByIdIncludingDeleted(hubId)).willReturn(Optional.of(hub));
        given(hubRepository.completeIfPending(hubId)).willReturn(1);

        // when
        hubCreationSagaService.complete(hubId);

        // then
        then(hubRepository).should().completeIfPending(hubId);
        then(hubRelationRepository).shouldHaveNoInteractions();
        then(hubEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("허브 경로 생성 완료가 중복 수신되면 후속 처리를 수행하지 않는다")
    void complete_whenPendingTransitionSkipped_doesNotPublishFollowUpEvent() {
        // given
        UUID hubId = UUID.randomUUID();
        Hub hub = createHub(hubId);
        given(hubRepository.findByIdIncludingDeleted(hubId)).willReturn(Optional.of(hub));
        given(hubRepository.completeIfPending(hubId)).willReturn(0);

        // when
        hubCreationSagaService.complete(hubId);

        // then
        then(hubRepository).should().completeIfPending(hubId);
        then(hubRelationRepository).shouldHaveNoInteractions();
        then(hubEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("허브 경로 생성 보상은 PENDING 상태 전이에 성공했을 때만 삭제 이벤트를 발행한다")
    void compensate_whenPendingTransitionSucceeded_publishesDeletedEvent() {
        // given
        UUID hubId = UUID.randomUUID();
        Hub hub = createHub(hubId);
        given(hubRepository.findByIdIncludingDeleted(hubId)).willReturn(Optional.of(hub));
        given(hubRepository.failIfPending(eq(hubId), any(LocalDateTime.class), eq(SYSTEM_USER_ID))).willReturn(1);

        // when
        hubCreationSagaService.compensate(hubId, "route build failed");

        // then
        ArgumentCaptor<HubDeletedEvent> eventCaptor = ArgumentCaptor.forClass(HubDeletedEvent.class);
        then(hubRelationRepository).should().deleteByHubId(hubId);
        then(hubEventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getHubId()).isEqualTo(hubId);
        assertThat(eventCaptor.getValue().getDeletedBy()).isEqualTo(SYSTEM_USER_ID);
    }

    @Test
    @DisplayName("허브 경로 생성 보상이 중복 수신되면 관계 삭제와 삭제 이벤트 발행을 수행하지 않는다")
    void compensate_whenPendingTransitionSkipped_doesNotPublishFollowUpEvent() {
        // given
        UUID hubId = UUID.randomUUID();
        Hub hub = createHub(hubId);
        given(hubRepository.findByIdIncludingDeleted(hubId)).willReturn(Optional.of(hub));
        given(hubRepository.failIfPending(eq(hubId), any(LocalDateTime.class), eq(SYSTEM_USER_ID))).willReturn(0);

        // when
        hubCreationSagaService.compensate(hubId, "duplicated dlq");

        // then
        then(hubRelationRepository).should(never()).deleteByHubId(any(UUID.class));
        then(hubEventPublisher).should(never()).publishEvent(any(HubDeletedEvent.class));
    }

    private static Hub createHub(UUID hubId) {
        return Hub.builder()
                .hubId(hubId)
                .name("테스트 허브")
                .hubType(HubType.CENTER)
                .address(Address.of("서울시 송파구", Coordinate.of(37.5, 127.1)))
                .build();
    }
}
