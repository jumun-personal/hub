package com.jumunhasyeo.hub.hubRoute.domain.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteBuildJob;

import java.util.Optional;
import java.util.UUID;

public interface HubRouteBuildJobRepository {
    void request(Hub hub, int pairCount);
    Optional<HubRouteBuildJob> findByHubId(UUID hubId);
    Optional<HubRouteBuildJobCounter> completePair(UUID hubId);
    Optional<HubRouteBuildJobCounter> failPair(UUID hubId, String reason);
    int retryFailed(UUID hubId, int remainingCount);
    void addRemaining(UUID hubId, int pairCount);
    void complete(UUID hubId);
    int fail(UUID hubId, String reason);
    void cancel(UUID hubId, String reason);
    void lockTopology();

    record HubRouteBuildJobCounter(UUID hubId, int remainingCount, int failedCount) {
        public boolean isTerminal() {
            return remainingCount == 0;
        }
    }
}
