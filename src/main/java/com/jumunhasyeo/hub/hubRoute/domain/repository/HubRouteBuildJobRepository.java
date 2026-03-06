package com.jumunhasyeo.hub.hubRoute.domain.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteBuildJob;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface HubRouteBuildJobRepository {
    void request(Hub hub);
    Optional<HubRouteBuildJobClaim> claimPlanning(LocalDateTime staleBefore);
    Optional<HubRouteBuildJob> findByHubId(UUID hubId);
    int initialize(UUID hubId, UUID processingToken, int pairCount);
    Optional<HubRouteBuildJobCounter> completePair(UUID hubId);
    Optional<HubRouteBuildJobCounter> failPair(UUID hubId, String reason);
    int retryFailed(UUID hubId, int remainingCount);
    void addRemaining(UUID hubId, int pairCount);
    void complete(UUID hubId);
    int fail(UUID hubId, String reason);
    void cancel(UUID hubId, String reason);
    void lockFinalization();

    record HubRouteBuildJobClaim(UUID hubId, UUID processingToken) {
    }

    record HubRouteBuildJobCounter(UUID hubId, int remainingCount, int failedCount) {
        public boolean isTerminal() {
            return remainingCount == 0;
        }
    }
}
