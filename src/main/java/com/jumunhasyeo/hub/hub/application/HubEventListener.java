package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.hub.infrastructure.outbox.OutboxService;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubDeletedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubNameUpdatedEvent;
import com.jumunhasyeo.hub.hub.domain.event.HubUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubEventListener {

    private final OutboxService outboxService;

    /**
     * Hub 생성 outbox 저장
     * 트랜잭션 커밋 전 실행
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleHubCreated(HubCreatedEvent event) {
        log.info("Hub created sync event received: {} (ID: {})",
                event.getName(),
                event.getHubId());

        outboxService.save(event);
    }

    /**
     * Hub 삭제 outbox 저장
     * 트랜잭션 커밋 전 실행
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleHubDeleted(HubDeletedEvent event) {
        log.info("Hub deleted sync event received: {} (ID: {})",
                event.getName(),
                event.getHubId());

        outboxService.save(event);
    }

    /**
     * Hub 이름 갱신 outbox 저장
     * 트랜잭션 커밋 전 실행
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleHubNameUpdated(HubNameUpdatedEvent event) {
        log.info("Hub NameUpdated sync event received: {} (ID: {})",
                event.getName(),
                event.getHubId());

        outboxService.save(event);
    }

    /**
     * Hub 이름 갱신 outbox 저장
     * 트랜잭션 커밋 전 실행
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleHubUpdated(HubUpdatedEvent event) {
        log.info("HubUpdatedEvent sync event received: (ID: {})",
                event.getHubId());

        outboxService.save(event);
    }

    /**
     * Hub 생성 kafka 발송
     * 트랜잭션 커밋 후 비동기 실행
     */
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void asyncHandleHubUpdated(HubUpdatedEvent event) {
        log.info("HubUpdatedEvent async event received: (ID: {})",
                event.getHubId());

        outboxService.publishAfterCommit(event.getEventKey());
    }

    /**
     * Hub 생성 kafka 발송
     * 트랜잭션 커밋 후 비동기 실행
     */
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void asyncHandleHubCreated(HubCreatedEvent event) {
        log.info("Hub created async event received: {} (ID: {})",
                event.getName(),
                event.getHubId());

        outboxService.publishAfterCommit(event.getEventKey());
    }

    /**
     * Hub 삭제 kafka 발송
     * 트랜잭션 커밋 후 비동기 실행
     */
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void asyncHandleHubDeleted(HubDeletedEvent event) {
        log.info("Hub deleted async event received: {} (ID: {})",
                event.getName(),
                event.getHubId());

        outboxService.publishAfterCommit(event.getEventKey());
    }

    /**
     * Hub 이름 갱신 kafka 발송
     * 트랜잭션 커밋 후 비동기 실행
     */
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void asyncHandleNameUpdated(HubNameUpdatedEvent event) {
        log.info("Hub NameUpdated async event received: {} (ID: {})",
                event.getName(),
                event.getHubId());

        outboxService.publishAfterCommit(event.getEventKey());
    }
}
