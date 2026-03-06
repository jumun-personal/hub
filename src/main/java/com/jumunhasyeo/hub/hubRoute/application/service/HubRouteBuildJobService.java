package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.HubRouteBuildJobRes;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteBuildJob;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteBuildJobStatus;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteBuildJobRepository;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteBuildJobRepository.HubRouteBuildJobClaim;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteBuildJobRepository.HubRouteBuildJobCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HubRouteBuildJobService {

    private final HubRouteBuildJobRepository buildJobRepository;

    @Transactional
    public void request(final Hub hub) {
        buildJobRepository.request(hub);
    }

    @Transactional
    public Optional<HubRouteBuildJobClaim> claimPlanning(final LocalDateTime staleBefore) {
        return buildJobRepository.claimPlanning(staleBefore);
    }

    @Transactional(readOnly = true)
    public HubRouteBuildJobRes get(final UUID hubId) {
        return HubRouteBuildJobRes.from(findJob(hubId));
    }

    @Transactional
    public void initialize(final UUID hubId, final UUID processingToken, final int pairCount) {
        int updated = buildJobRepository.initialize(hubId, processingToken, pairCount);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.PROCESSING_CONFLICT_EXCEPTION);
        }
    }

    @Transactional
    public Optional<HubRouteBuildJobCounter> completePair(final UUID hubId) {
        return buildJobRepository.completePair(hubId);
    }

    @Transactional
    public Optional<HubRouteBuildJobCounter> failPair(final UUID hubId, final String reason) {
        return buildJobRepository.failPair(hubId, reason);
    }

    @Transactional
    public void retryFailed(final UUID hubId, final int failedRouteRowCount) {
        HubRouteBuildJob job = findJob(hubId);
        if (job.getStatus() != HubRouteBuildJobStatus.FAILED) {
            throw new BusinessException(ErrorCode.PROCESSING_CONFLICT_EXCEPTION);
        }

        int remainingPairCount = failedRouteRowCount / 2;
        int updated = buildJobRepository.retryFailed(hubId, remainingPairCount);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.PROCESSING_CONFLICT_EXCEPTION);
        }
    }

    @Transactional
    public void addRemaining(final UUID hubId, final int pairCount) {
        if (pairCount > 0) {
            buildJobRepository.addRemaining(hubId, pairCount);
        }
    }

    @Transactional
    public void complete(final UUID hubId) {
        buildJobRepository.complete(hubId);
    }

    @Transactional
    public void fail(final UUID hubId, final String reason) {
        int updated = buildJobRepository.fail(hubId, reason);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.PROCESSING_CONFLICT_EXCEPTION);
        }
    }

    @Transactional
    public void cancel(final UUID hubId, final String reason) {
        buildJobRepository.cancel(hubId, reason);
    }

    @Transactional
    public void lockFinalization() {
        buildJobRepository.lockFinalization();
    }

    private HubRouteBuildJob findJob(final UUID hubId) {
        return buildJobRepository.findByHubId(hubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HUB_ROUTE_NOT_FOUND));
    }
}
