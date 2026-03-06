package com.jumunhasyeo.common.scheduler;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxPublicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "hub.outbox.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPollingScheduler {

    private final OutboxPublicationService outboxPublicationService;

    @Async("schedulerExecutor")
    @SchedulerLock(name = "outboxPolling", lockAtLeastFor = "5s")
    @Scheduled(fixedDelay = 5000) // 2초마다 Polling
    public void pollOutbox() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(5);
        outboxPublicationService.publishPending(staleBefore);
    }



    @Async("schedulerExecutor")
    @SchedulerLock(name = "outboxPollingCleanup")
    @Scheduled(cron = "0 0 3 * * *") // 매일 03:00 7일 지난 완료된 이벤트 정리
    public void cleanupOutbox() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deletedComplete = outboxPublicationService.cleanupCompletedBefore(cutoff);
        log.info("Deleted {} completed outbox events", deletedComplete);
    }
}
