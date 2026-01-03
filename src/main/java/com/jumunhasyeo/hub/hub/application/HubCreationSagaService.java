package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.repository.HubRelationRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class HubCreationSagaService {
    private final HubRepository hubRepository;
    private final HubRelationRepository hubRelationRepository;
    private final HubEventPublisher hubEventPublisher;

    @Value("${hub.compensation.deleted-by:0}")
    private Long deletedBy;

    @Transactional
    public void complete(UUID hubId) {
        Hub hub = hubRepository.findByIdIncludingCreating(hubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HUB_NOT_FOUND));
        if (hub.isDeleted()) {
            log.info("Hub already deleted, skip completion. hubId={}", hubId);
            return;
        }
        if (hub.isActive()) {
            log.info("Hub already active, skip completion. hubId={}", hubId);
            return;
        }
        hub.activate();
        hubRepository.save(hub);
    }

    @Transactional
    public void compensate(UUID hubId, String reason) {
        Hub hub = hubRepository.findByIdIncludingCreating(hubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HUB_NOT_FOUND));
        if (hub.isDeleted()) {
            log.info("Hub already deleted, skip compensation. hubId={}", hubId);
            return;
        }
        if (hub.isActive()) {
            log.info("Hub already active, skip compensation. hubId={}", hubId);
            return;
        }
        log.warn("Compensating hub creation. hubId={}, reason={}", hubId, reason);
        hubRelationRepository.deleteByHubId(hubId);
        hub.markFailed(deletedBy);
        hubRepository.save(hub);
        hubEventPublisher.publishEvent(HubDeletedEvent.from(hub, deletedBy));
    }
}
