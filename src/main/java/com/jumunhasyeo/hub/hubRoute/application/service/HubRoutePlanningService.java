package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hub.application.HubCreationSagaService;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteBuildJobRepository.HubRouteBuildJobCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HubRoutePlanningService {

    private final HubRouteBuildJobService buildJobService;
    private final HubRouteService hubRouteService;
    private final HubCreationSagaService hubCreationSagaService;

    @Transactional
    public void finalizeIfTerminal(final HubRouteBuildJobCounter counter, final String failureReason) {
        if (!counter.isTerminal()) {
            return;
        }
        if (counter.failedCount() > 0) {
            buildJobService.fail(counter.hubId(), failureReason);
            hubCreationSagaService.failRouteBuild(counter.hubId(), failureReason);
            return;
        }

        buildJobService.lockTopology();
        int missingPairCount = hubRouteService.prepareRoutesForBuildJob(counter.hubId());
        if (missingPairCount > 0) {
            buildJobService.addRemaining(counter.hubId(), missingPairCount);
            log.info("Hub route build found missing routes during finalization. hubId={}, pairCount={}",
                    counter.hubId(), missingPairCount);
            return;
        }

        buildJobService.complete(counter.hubId());
        hubCreationSagaService.complete(counter.hubId());
    }

    @Transactional
    public void retryFailed(final UUID hubId) {
        hubCreationSagaService.retryRouteBuild(hubId);
        int failedRouteRows = hubRouteService.resetFailedBuildRoutes(hubId);
        if (failedRouteRows == 0) {
            buildJobService.lockTopology();
            int missingPairCount = hubRouteService.prepareRoutesForBuildJob(hubId);
            buildJobService.retryFailed(hubId, missingPairCount * 2);
            if (missingPairCount == 0) {
                hubCreationSagaService.complete(hubId);
            }
            return;
        }
        buildJobService.retryFailed(hubId, failedRouteRows);
    }
}
