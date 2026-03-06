package com.jumunhasyeo.common.scheduler;

import com.jumunhasyeo.hub.hubRoute.application.service.HubRoutePlanningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hub.route.planning.scheduler.enabled", havingValue = "true")
public class HubRoutePlanningScheduler {

    private final HubRoutePlanningService planningService;

    @Value("${hub.route.planning.stale-timeout:5m}")
    private String stalePlanningTimeout;

    @Scheduled(fixedDelayString = "${hub.route.planning.fixed-delay-ms:500}")
    @SchedulerLock(name = "hubRoutePlanning", lockAtLeastFor = "PT0.1S", lockAtMostFor = "PT5S")
    public void planNextBuildJob() {
        LocalDateTime staleBefore = LocalDateTime.now().minus(stalePlanningTimeout());
        planningService.claimPlanning(staleBefore)
                .ifPresent(claim -> {
                    try {
                        planningService.initialize(claim);
                    } catch (RuntimeException e) {
                        log.warn("Hub route planning failed. hubId={}", claim.hubId(), e);
                    }
                });
    }

    private Duration stalePlanningTimeout() {
        return DurationStyle.detectAndParse(stalePlanningTimeout);
    }
}
