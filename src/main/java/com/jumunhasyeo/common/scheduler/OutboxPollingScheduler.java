package com.jumunhasyeo.common.scheduler;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPollingScheduler {

    private final OutboxService outboxService;

    @Async("schedulerExecutor")
    @SchedulerLock(name = "outboxPolling", lockAtLeastFor = "5s")
    @Scheduled(fixedDelay = 5000) // 2초마다 Polling
    public void pollOutbox() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(5);
        outboxService.processClaimableEvents(staleBefore);
    }



    @Async("schedulerExecutor")
    @SchedulerLock(name = "outboxPollingCleanup")
    @Scheduled(cron = "0 0 3 * * *") // 매일 03:00 7일 지난 완료된 이벤트 정리
    public void cleanupOutbox() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deletedComplete = outboxService.cleanUp(cutoff);
        log.info("Deleted {} completed outbox events", deletedComplete);
    }
}
