package com.jumunhasyeo.common.scheduler;

import com.jumunhasyeo.common.Idempotency.db.domain.IdempotencyKey;
import com.jumunhasyeo.common.Idempotency.db.domain.repository.IdempotencyKeyRepository;
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
public class IdempotentScheduler {

    private final IdempotencyKeyRepository repository;

    /**
     * 만료된 멱등키 정리 (매일 새벽 2시)
     */
    @Async("schedulerExecutor")
    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "cleanupExpiredKeys")
    @Transactional
    public void cleanupExpiredKeys() {
        log.info("Starting cleanup of expired idempotency keys");

        LocalDateTime now = LocalDateTime.now();
        List<IdempotencyKey> expiredKeys = repository.findExpiredKeys(now);

        if (!expiredKeys.isEmpty()) {
            repository.deleteAll(expiredKeys);
            log.info("Cleaned up {} expired idempotency keys", expiredKeys.size());
        } else {
            log.info("No expired keys to clean up");
        }
    }

    /**
     * 오래된 PROCESSING 상태 정리 (5분마다)
     * 애플리케이션 크래시로 영원히 PROCESSING 상태인 경우 처리
     */
    @Async("schedulerExecutor")
    @Scheduled(fixedRate = 300000) // 5분
    @SchedulerLock(
            name = "cleanupStaleProcessing",
            lockAtLeastFor = "5m"  // 주기와 동일
    )
    @Transactional
    public void cleanupStaleProcessing() {
        log.debug("Checking for stale PROCESSING keys");

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<IdempotencyKey> staleKeys = repository.findStaleProcessingKeys(threshold);

        if (!staleKeys.isEmpty()) {
            staleKeys.forEach(key -> {
                key.applyError("Timeout: Processing took too long");
                repository.save(key);
            });
            log.warn("Found and updated {} stale PROCESSING keys", staleKeys.size());
        }
    }
}
