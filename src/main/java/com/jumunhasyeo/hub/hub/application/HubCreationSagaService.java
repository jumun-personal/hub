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

import java.time.LocalDateTime;
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
        Hub hub = hubRepository.findByIdIncludingDeleted(hubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HUB_NOT_FOUND));
        int updated = hubRepository.completeIfPending(hubId);
        if (updated == 1) {
            log.info("Hub route build completed. hubId={}", hubId);
            return;
        }
        log.info("Hub completion skipped. hubId={}, status={}, deleted={}", hubId, hub.getStatus(), hub.isDeleted());
    }

    @Transactional
    public void compensate(UUID hubId, String reason) {
        Hub hub = hubRepository.findByIdIncludingDeleted(hubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HUB_NOT_FOUND));

        int updated = hubRepository.failIfPending(hubId, LocalDateTime.now(), deletedBy);
        if (updated == 0) {
            log.info("Hub compensation skipped. hubId={}, status={}, deleted={}", hubId, hub.getStatus(), hub.isDeleted());
            return;
        }

        log.warn("Compensating hub creation. hubId={}, reason={}", hubId, reason);
        hubRelationRepository.deleteByHubId(hubId);
        hubEventPublisher.publishEvent(HubDeletedEvent.from(hub, deletedBy));
    }
}
