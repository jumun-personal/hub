package com.jumunhasyeo.common.scheduler;

import com.jumunhasyeo.common.inbox.InboxEvent;
import com.jumunhasyeo.common.inbox.InboxService;
import com.jumunhasyeo.common.inbox.InboxStatus;
import com.jumunhasyeo.common.inbox.JpaInboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InboxPollingScheduler {

    private final InboxService inboxService;
    private final JpaInboxRepository inboxRepository;

    /**
     * RECEIVED/PROCESSING 상태 이벤트 재처리
     * 3초마다 폴링
     */
    @Async("schedulerExecutor")
    @SchedulerLock(name = "inboxPolling", lockAtLeastFor = "3s")
    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void retryProcessingEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<InboxEvent> receivedEvents = inboxService.findByStatusAndModifiedAtBefore(
                InboxStatus.RECEIVED,
                now.minusSeconds(1)
        );
        List<InboxEvent> stuckProcessingEvents = inboxService.findByStatusAndModifiedAtBefore(
                InboxStatus.PROCESSING,
                now.minusSeconds(10)
        );

        int total = receivedEvents.size() + stuckProcessingEvents.size();
        if (total > 0) {
            log.info(
                    "Found {} inbox events to process (received={}, processing={})",
                    total,
                    receivedEvents.size(),
                    stuckProcessingEvents.size()
            );
        }

        for (InboxEvent event : receivedEvents) {
            inboxService.inboxProcess(event);
        }
        for (InboxEvent event : stuckProcessingEvents) {
            inboxService.inboxProcess(event);
        }
    }

    @Async("schedulerExecutor")
    @SchedulerLock(name = "inboxPollingCleanup")
    @Scheduled(cron = "0 0 3 * * *") // 매일 03:00 7일 지난 완료된 이벤트 정리
    @Transactional
    public void cleanupCompletedEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        
        int deleted = inboxRepository.deleteByStatusAndModifiedAtBefore(
                InboxStatus.COMPLETED, cutoff
        );
        
        log.info("Deleted {} completed inbox events", deleted);
    }
}
