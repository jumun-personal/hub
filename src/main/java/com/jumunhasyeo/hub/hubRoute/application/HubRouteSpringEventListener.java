package com.jumunhasyeo.hub.hubRoute.application;

import com.jumunhasyeo.common.outbox.OutboxService;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildCompletedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildFailedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteCreatedEvent;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubRouteSpringEventListener {

    private final OutboxService outboxService;

    /**
     * HubRoute 생성 outbox 저장
     * 트랜잭션 커밋 전 실행
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleHubRouteCreated(HubRouteCreatedEvent event) {
        log.info("sync HubRouteCreatedEvent received size: {} ", event.getRouteId());
        outboxService.save(event);
    }

    /**
     * HubRoute 삭제 outbox 저장
     * 트랜잭션 커밋 전 실행
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleHubRouteDeleted(HubRouteDeletedEvent event) {
        log.info("sync HubRouteDeletedEvent received size: {} ", event.getRouteId());
        outboxService.save(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleHubRouteBuildCompleted(HubRouteBuildCompletedEvent event) {
        log.info("sync HubRouteBuildCompletedEvent received hubId: {} ", event.getHubId());
        outboxService.save(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleHubRouteBuildFailed(HubRouteBuildFailedEvent event) {
        log.info("sync HubRouteBuildFailedEvent received hubId: {} ", event.getHubId());
        outboxService.save(event);
    }

    /**
     * HubRoute 생성 outbox 저장
     * 트랜잭션 커밋 전 실행
     */
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void asyncHandleHubRouteCreated(HubRouteCreatedEvent event) {
        log.info("async HubRouteCreatedEvent received size: {} ", event.getRouteId());
        outboxService.publishAfterCommit(event.getEventKey());
    }

    /**
     * HubRoute 삭제 outbox 저장
     * 트랜잭션 커밋 전 실행
     */
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void asyncHandleHubRouteDeleted(HubRouteDeletedEvent event) {
        log.info("async HubRouteDeletedEvent received size: {} ", event.getRouteId());

        outboxService.publishAfterCommit(event.getEventKey());

    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void asyncHandleHubRouteBuildCompleted(HubRouteBuildCompletedEvent event) {
        log.info("async HubRouteBuildCompletedEvent received hubId: {} ", event.getHubId());
        outboxService.publishAfterCommit(event.getEventKey());
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void asyncHandleHubRouteBuildFailed(HubRouteBuildFailedEvent event) {
        log.info("async HubRouteBuildFailedEvent received hubId: {} ", event.getHubId());
        outboxService.publishAfterCommit(event.getEventKey());
    }
}
