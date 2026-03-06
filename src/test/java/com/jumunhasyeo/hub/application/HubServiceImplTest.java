package com.jumunhasyeo.hub.application;


import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.application.HubEventPublisher;
import com.jumunhasyeo.hub.hub.application.HubServiceImpl;
import com.jumunhasyeo.hub.hub.application.command.CreateHubCommand;
import com.jumunhasyeo.hub.hub.application.command.DeleteHubCommand;
import com.jumunhasyeo.hub.hub.application.command.UpdateHubCommand;
import com.jumunhasyeo.hub.hub.application.dto.response.HubRes;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubNameUpdatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubUpdatedEvent;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepositoryCustom;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hub.presentation.dto.HubSearchCondition;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteBuildJobService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderAvailabilityService;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HubServiceImplTest {

    @Mock
    private HubRepository hubRepository;
    @Mock
    private HubRepositoryCustom hubRepositoryCustom;
    @Mock
    private HubEventPublisher eventPublisher;
    @Mock
    private RouteProviderAvailabilityService routeProviderAvailabilityService;
    @Mock
    private HubRouteService hubRouteService;
    @Mock
    private HubRouteBuildJobService hubRouteBuildJobService;
    @InjectMocks
    private HubServiceImpl hubService;

    private static Hub createHub(UUID hubId) {
        return Hub.builder()
                .hubId(hubId)
                .centerHubRelations(new HashSet<>())
                .branchHubRelations(new HashSet<>())
                .name("송파 허브")
                .hubType(HubType.CENTER)
                .address(Address.of("street", Coordinate.of(12.6, 12.6)))
                .build();
    }

    @Test
    @DisplayName("hub를 생성할 수 있다.")
    public void create_CenterHub_hub_success() {
        //given
        CreateHubCommand command = CreateHubCommand.createCenter("이름", "주소", 12.7, 12.7, HubType.CENTER);
        //when
        HubRes hubRes = hubService.create(command);
        //then
        assertThat(hubRes.name()).isEqualTo(command.name());
        assertThat(hubRes.address()).isEqualTo(command.address());
        assertThat(hubRes.latitude()).isEqualTo(command.latitude());
        assertThat(hubRes.longitude()).isEqualTo(command.longitude());
    }

    @Test
    @DisplayName("hub 생성 요청에서는 경로가 완료되기 전 HubCreatedEvent를 발행하지 않는다")
    void createHub_doesNotPublishCreatedEventBeforeRouteBuildCompletes() {
        // given

        CreateHubCommand command = CreateHubCommand.createCenter("이름", "주소", 12.7, 12.7, HubType.CENTER);
        hubService.create(command);

        //then
        verify(eventPublisher, never()).publishEvent(any(HubCreatedEvent.class));
    }

    @Test
    @DisplayName("지도 Provider 상태와 무관하게 hub 생성과 Job 요청을 저장한다")
    void create_whenRouteProvidersUnavailable_stillRequestsRouteBuildJob() {
        // given
        CreateHubCommand command = CreateHubCommand.createCenter("이름", "주소", 12.7, 12.7, HubType.CENTER);

        // when
        hubService.create(command);

        // then
        verify(hubRepository).save(any(Hub.class));
        verify(hubRouteBuildJobService).request(any(Hub.class));
        verify(eventPublisher, never()).publishEvent(any(HubCreatedEvent.class));
    }

    @Test
    @DisplayName("hub를 수정할 수 있다.")
    public void update_hub_success() {
        //given
        UUID hubId = UUID.randomUUID();
        Hub savedHub = createHub(hubId);
        UpdateHubCommand command = new UpdateHubCommand(hubId, "이름", "주소", 12.7, 12.7);
        when(hubRepository.findById(any())).thenReturn(Optional.of(savedHub));
        //when
        HubRes hubRes = hubService.update(command);
        //then
        assertThat(hubRes.id()).isEqualTo(command.hubId());
        assertThat(hubRes.name()).isEqualTo(command.name());
        assertThat(hubRes.address()).isEqualTo(command.address());
        assertThat(hubRes.latitude()).isEqualTo(command.latitude());
        assertThat(hubRes.longitude()).isEqualTo(command.longitude());
    }

    @Test
    @DisplayName("hub를 수정할 때 없는 허브를 조회하지 못하면 예외 반환")
    public void update_hubIdIsIncorrect_ShouldThrowException() {
        //given
        UUID incorrectHubId = UUID.randomUUID();
        UpdateHubCommand command = new UpdateHubCommand(incorrectHubId, "이름", "주소", 12.7, 12.7);
        when(hubRepository.findById(any())).thenReturn(Optional.empty());

        //when
        BusinessException businessException = assertThrows(
                BusinessException.class, () -> hubService.update(command)
        );

        //then
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.HUB_NOT_FOUND);
    }

    @Test
    @DisplayName("hub를 ID로 단건 조회할 수 있다.")
    public void getById_Hub_Success() {
        Hub hub = createHub(UUID.randomUUID());
        when(hubRepository.findById(any())).thenReturn(Optional.of(hub));

        //when
        HubRes hubRes = hubService.getById(hub.getHubId());

        //then
        assertThat(hubRes.id()).isEqualTo(hub.getHubId());
    }

    @Test
    @DisplayName("hub를 단건 조회할 때 조회하지 못하면 예외 반환")
    public void getById_hubIdIsIncorrect_ShouldThrowException() {
        UUID incorrectHubId = UUID.randomUUID();
        when(hubRepository.findById(any())).thenReturn(Optional.empty());

        //when
        BusinessException businessException = assertThrows(
                BusinessException.class, () -> hubService.getById(incorrectHubId)
        );

        //then
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.HUB_NOT_FOUND);
    }

    @Test
    @DisplayName("hub를 검색 조회할 수 있다.")
    public void search_Hub_Success() {
        Hub hub = createHub(UUID.randomUUID());
        HubSearchCondition condition = HubSearchCondition.builder().name(hub.getName()).build();
        Pageable pageRequest = PageRequest.of(0, 10);
        Page<HubRes> page = new PageImpl<>(List.of(HubRes.from(hub)), PageRequest.of(0, 10), 0);
        when(hubRepositoryCustom.searchHubsByCondition(any(), any())).thenReturn(page);

        //when
        Page<HubRes> hubRes = hubService.search(condition, pageRequest);

        //then
        assertThat(hubRes.getContent().get(0).id()).isEqualTo(hub.getHubId());
    }

    @Test
    @DisplayName("hub를 논리 삭제할 수 있다.")
    public void delete_Hub_Success() {
        //given
        Hub hub = createHub(UUID.randomUUID());
        DeleteHubCommand command = new DeleteHubCommand(hub.getHubId(), 1L);
        when(hubRepository.findById(any())).thenReturn(Optional.of(hub));
        //when
        UUID deletedId = hubService.delete(command);

        //then
        assertThat(deletedId).isEqualTo(hub.getHubId());
    }

    @Test
    @DisplayName("지점 허브(BRANCH)를 생성할 수 있다.")
    public void create_BranchHub_success() {
        //given
        UUID centerHubId = UUID.randomUUID();
        Hub centerHub = createHub(centerHubId);
        when(hubRepository.findById(centerHubId)).thenReturn(Optional.of(centerHub));

        CreateHubCommand command = CreateHubCommand.createBranch(
                centerHubId, "강남 지점", "강남구 테헤란로", 37.5, 127.0, HubType.BRANCH
        );

        //when
        HubRes hubRes = hubService.create(command);

        //then
        assertThat(hubRes.name()).isEqualTo(command.name());
        assertThat(hubRes.address()).isEqualTo(command.address());
        assertThat(hubRes.latitude()).isEqualTo(command.latitude());
        assertThat(hubRes.longitude()).isEqualTo(command.longitude());
    }

    @Test
    @DisplayName("지점 허브 생성 시 중앙 허브가 없으면 예외가 발생한다.")
    public void create_BranchHub_centerHubNotFound_ShouldThrowException() {
        //given
        UUID incorrectCenterHubId = UUID.randomUUID();
        CreateHubCommand command = CreateHubCommand.createBranch(
                incorrectCenterHubId, "강남 지점", "강남구", 37.5, 127.0, HubType.BRANCH
        );
        when(hubRepository.findById(incorrectCenterHubId)).thenReturn(Optional.empty());

        //when
        BusinessException businessException = assertThrows(
                BusinessException.class, () -> hubService.create(command)
        );

        //then
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.HUB_NOT_FOUND);
    }

    @Test
    @DisplayName("지점 허브 생성 요청에서는 경로가 완료되기 전 HubCreatedEvent를 발행하지 않는다")
    void createBranchHub_doesNotPublishCreatedEventBeforeRouteBuildCompletes() {
        //given
        UUID centerHubId = UUID.randomUUID();
        Hub centerHub = createHub(centerHubId);
        when(hubRepository.findById(centerHubId)).thenReturn(Optional.of(centerHub));

        CreateHubCommand command = CreateHubCommand.createBranch(
                centerHubId, "강남 지점", "강남구", 37.5, 127.0, HubType.BRANCH
        );

        //when
        hubService.create(command);

        //then
        verify(eventPublisher, never()).publishEvent(any(HubCreatedEvent.class));
    }

    @Test
    @DisplayName("중앙 허브 생성 요청에서는 경로가 완료되기 전 HubCreatedEvent를 발행하지 않는다")
    void createCenterHub_doesNotPublishCreatedEventBeforeRouteBuildCompletes() {
        //given
        CreateHubCommand command = CreateHubCommand.createCenter(
                "서울 중앙 허브", "서울시 송파구", 37.5, 127.0, HubType.CENTER
        );

        //when
        hubService.create(command);

        //then
        verify(eventPublisher, never()).publishEvent(any(HubCreatedEvent.class));
    }

    @Test
    @DisplayName("hub 삭제 시 HubDeletedEvent가 발행된다.")
    void delete_Hub_ShouldPublishDeletedEvent() {
        //given
        UUID hubId = UUID.randomUUID();
        Hub hub = createHub(hubId);
        Long userId = 1L;
        DeleteHubCommand command = new DeleteHubCommand(hubId, userId);
        when(hubRepository.findById(hubId)).thenReturn(Optional.of(hub));

        //when
        hubService.delete(command);

        //then
        ArgumentCaptor<HubDeletedEvent> eventCaptor = ArgumentCaptor.forClass(HubDeletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        HubDeletedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getHubId()).isEqualTo(hubId);
        assertThat(capturedEvent.getDeletedBy()).isEqualTo(userId);
    }

    @Test
    @DisplayName("hub 삭제 시 존재하지 않는 허브면 예외가 발생한다.")
    public void delete_HubNotFound_ShouldThrowException() {
        //given
        UUID incorrectHubId = UUID.randomUUID();
        DeleteHubCommand command = new DeleteHubCommand(incorrectHubId, 1L);
        when(hubRepository.findById(incorrectHubId)).thenReturn(Optional.empty());

        //when
        BusinessException businessException = assertThrows(
                BusinessException.class, () -> hubService.delete(command)
        );

        //then
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.HUB_NOT_FOUND);
    }

    @Test
    @DisplayName("허브 이름이 변경되지 않으면 HubNameUpdatedEvent를 발행하지 않는다.")
    void update_same_name_should_not_publish_name_updated() {
        UUID hubId = UUID.randomUUID();
        Hub savedHub = createHub(hubId);
        UpdateHubCommand command = new UpdateHubCommand(hubId, savedHub.getName(), "새 주소", 33.3, 126.5);
        when(hubRepository.findById(hubId)).thenReturn(Optional.of(savedHub));

        hubService.update(command);

        verify(eventPublisher, never()).publishEvent(any(HubNameUpdatedEvent.class));
        verify(eventPublisher).publishEvent(any(HubUpdatedEvent.class));
    }

    @Test
    @DisplayName("existById는 repository 결과를 그대로 반환한다.")
    void existById_success() {
        UUID hubId = UUID.randomUUID();
        when(hubRepository.existById(hubId)).thenReturn(Boolean.TRUE);

        Boolean result = hubService.existById(hubId);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("getAll은 전체 hub를 HubRes로 매핑한다.")
    void getAll_success() {
        Hub hub1 = createHub(UUID.randomUUID());
        Hub hub2 = Hub.builder()
                .hubId(UUID.randomUUID())
                .centerHubRelations(new HashSet<>())
                .branchHubRelations(new HashSet<>())
                .name("강남 허브")
                .hubType(HubType.BRANCH)
                .address(Address.of("new street", Coordinate.of(37.1, 127.2)))
                .build();
        when(hubRepository.findAll()).thenReturn(List.of(hub1, hub2));

        List<HubRes> result = hubService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(HubRes::id).containsExactly(hub1.getHubId(), hub2.getHubId());
    }
}
