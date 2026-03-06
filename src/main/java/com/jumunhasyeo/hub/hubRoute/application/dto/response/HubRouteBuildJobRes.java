package com.jumunhasyeo.hub.hubRoute.application.dto.response;

import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteBuildJob;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteBuildJobStatus;

import java.util.UUID;

public record HubRouteBuildJobRes(
        UUID hubId,
        HubRouteBuildJobStatus status,
        int totalCount,
        int remainingCount,
        int failedCount,
        int retryCount,
        String errorMessage
) {
    public static HubRouteBuildJobRes from(final HubRouteBuildJob job) {
        return new HubRouteBuildJobRes(
                job.getHubId(),
                job.getStatus(),
                job.getTotalCount(),
                job.getRemainingCount(),
                job.getFailedCount(),
                job.getRetryCount(),
                job.getErrorMessage()
        );
    }
}
