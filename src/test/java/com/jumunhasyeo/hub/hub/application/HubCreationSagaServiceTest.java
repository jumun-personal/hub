package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("허브 경로 생성 완료는 Hub를 COMPLETE로 전이하고 HubCreatedEvent를 발행한다")
    void complete_whenPendingTransitionSucceeded_publishesCreatedEvent() {
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
        ArgumentCaptor<HubCreatedEvent> eventCaptor = ArgumentCaptor.forClass(HubCreatedEvent.class);
        then(hubEventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getHubId()).isEqualTo(hubId);
        assertThat(eventCaptor.getValue().getCenterHubId()).isNull();
        assertThat(eventCaptor.getValue().eventKey()).isEqualTo("HubCreatedEvent:" + hubId);
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
    @DisplayName("지점 허브 경로 생성 완료 이벤트에는 중앙 허브 ID가 포함된다")
    void complete_branchHub_publishesCreatedEventWithCenterHubId() {
        // given
        UUID centerHubId = UUID.randomUUID();
        Hub centerHub = createHub(centerHubId);
        centerHub.activate();
        UUID branchHubId = UUID.randomUUID();
        Hub branchHub = Hub.builder()
                .hubId(branchHubId)
                .name("테스트 지점 허브")
                .hubType(HubType.BRANCH)
                .address(Address.of("서울시 강남구", Coordinate.of(37.4, 127.0)))
                .build();
        branchHub.addCenterHub(centerHub);
        given(hubRepository.findByIdIncludingDeleted(branchHubId)).willReturn(Optional.of(branchHub));
        given(hubRepository.completeIfPending(branchHubId)).willReturn(1);

        // when
        hubCreationSagaService.complete(branchHubId);

        // then
        ArgumentCaptor<HubCreatedEvent> eventCaptor = ArgumentCaptor.forClass(HubCreatedEvent.class);
        then(hubEventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getHubId()).isEqualTo(branchHubId);
        assertThat(eventCaptor.getValue().getCenterHubId()).isEqualTo(centerHubId);
    }

    @Test
    @DisplayName("전체 경로 구축 실패는 Hub를 FAILED로 전환하고 삭제 후속 처리를 수행하지 않는다")
    void failRouteBuild_whenPendingTransitionSucceeded_doesNotDeleteOrPublishEvent() {
        // given
        UUID hubId = UUID.randomUUID();
        given(hubRepository.failRouteBuildIfPending(hubId)).willReturn(1);

        // when
        hubCreationSagaService.failRouteBuild(hubId, "route build failed");

        // then
        then(hubRepository).should().failRouteBuildIfPending(hubId);
        then(hubRelationRepository).shouldHaveNoInteractions();
        then(hubEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("전체 경로 구축 실패 상태 전이가 충돌하면 예외를 반환한다")
    void failRouteBuild_whenTransitionConflicts_throwsException() {
        // given
        UUID hubId = UUID.randomUUID();
        given(hubRepository.failRouteBuildIfPending(hubId)).willReturn(0);

        // when & then
        assertThatThrownBy(() -> hubCreationSagaService.failRouteBuild(hubId, "route build failed"))
                .isInstanceOf(com.jumunhasyeo.common.exception.BusinessException.class);
        then(hubRelationRepository).shouldHaveNoInteractions();
        then(hubEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("운영자 재시도는 Hub를 FAILED에서 PENDING으로 전환하고 이벤트를 발행하지 않는다")
    void retryRouteBuild_whenFailedTransitionSucceeded_doesNotPublishEvent() {
        // given
        UUID hubId = UUID.randomUUID();
        given(hubRepository.retryRouteBuildIfFailed(hubId)).willReturn(1);

        // when
        hubCreationSagaService.retryRouteBuild(hubId);

        // then
        then(hubRepository).should().retryRouteBuildIfFailed(hubId);
        then(hubRelationRepository).shouldHaveNoInteractions();
        then(hubEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("FAILED가 아닌 Hub의 경로 구축 재시도는 예외를 반환한다")
    void retryRouteBuild_whenTransitionConflicts_throwsException() {
        // given
        UUID hubId = UUID.randomUUID();
        given(hubRepository.retryRouteBuildIfFailed(hubId)).willReturn(0);

        // when & then
        assertThatThrownBy(() -> hubCreationSagaService.retryRouteBuild(hubId))
                .isInstanceOf(com.jumunhasyeo.common.exception.BusinessException.class);
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
