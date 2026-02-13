package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.application.command.CreateHubCommand;
import com.jumunhasyeo.hub.hub.application.command.DeleteHubCommand;
import com.jumunhasyeo.hub.hub.application.command.UpdateHubCommand;
import com.jumunhasyeo.hub.hub.application.dto.response.HubRes;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubNameUpdatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubUpdatedEvent;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepositoryCustom;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hub.presentation.dto.HubSearchCondition;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderAvailabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class HubServiceImpl implements HubService{
    private final HubRepository hubRepository;
    private final HubRepositoryCustom hubRepositoryCustom;
    private final HubEventPublisher hubEventPublisher;
    private final RouteProviderAvailabilityService routeProviderAvailabilityService;

    @Transactional
    public HubRes create(CreateHubCommand command) {
        routeProviderAvailabilityService.assertRouteCreationAvailable();

        Coordinate coordinate = Coordinate.of(command.latitude(), command.longitude());
        Address address = Address.of(command.address(), coordinate);
        Hub hub = Hub.of(command.name(), address, command.hubType());

        switch (hub.getHubType()) {
            case BRANCH -> createBranchHub(command.centerHubId(), hub);
            case CENTER -> createCenterHub(hub);
            default -> throw new BusinessException(ErrorCode.INVALID_HUB_TYPE);
        }
        return HubRes.from(hub);
    }

    @Transactional
    public HubRes update(UpdateHubCommand command) {
        Hub hub = getHub(command.hubId());
        String preName = hub.getName();
        Coordinate coordinate = Coordinate.of(command.latitude(), command.longitude());
        Address address = Address.of(command.address(), coordinate);
        hub.update(command.name(), address);

        if(isChangedName(preName, hub)){
            hubEventPublisher.publishEvent(HubNameUpdatedEvent.of(hub));
        }
        hubEventPublisher.publishEvent(HubUpdatedEvent.of(hub));
        return HubRes.from(hub);
    }

    @Transactional
    public UUID delete(DeleteHubCommand command) {
        Hub hub = getHub(command.hubId());
        hub.delete(command.userId());
        hubEventPublisher.publishEvent(HubDeletedEvent.from(hub, command.userId()));
        return hub.getHubId();
    }

    @Override
    public Boolean existById(UUID uuid) {
        return hubRepository.existById(uuid);
    }

    @Override
    public List<HubRes> getAll() {
        return hubRepository.findAll()
                .stream()
                .map(HubRes::from)
                .collect(Collectors.toList());
    }

    public HubRes getById(UUID hubId) {
        return HubRes.from(getHub(hubId));
    }

    public Page<HubRes> search(HubSearchCondition condition, Pageable pageable) {
        return hubRepositoryCustom.searchHubsByCondition(condition, pageable);
    }

    private Hub getHub(UUID hubId) {
        return hubRepository.findById(hubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HUB_NOT_FOUND));
    }

    private void createCenterHub(Hub hub) {
        hubRepository.save(hub);
        hubEventPublisher.publishEvent(HubCreatedEvent.centerHub(hub));
    }

    private void createBranchHub(UUID centerHubId, Hub hub) {
        Hub centerHub = getHub(centerHubId);
        hub.addCenterHub(centerHub);
        hubRepository.save(hub);
        hubEventPublisher.publishEvent(HubCreatedEvent.branchHub(hub, centerHub.getHubId()));
    }

    private boolean isChangedName(String preName, Hub hub) {
        return !preName.equals(hub.getName());
    }
}
