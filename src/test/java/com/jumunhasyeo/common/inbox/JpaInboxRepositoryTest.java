package com.jumunhasyeo.stock.infrastructure.inbox;

import com.jumunhasyeo.testsupport.AbstractJpaRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JpaInboxRepositoryTest extends AbstractJpaRepositoryTest {

    @Autowired
    private JpaInboxRepository jpaInboxRepository;

    @Test
    @DisplayName("InboxEvent를 저장할 수 있다.")
    void save_InboxEvent_success() {
        //given
        InboxEvent event = createInboxEvent("test-key-1");

        //when
        InboxEvent savedEvent = jpaInboxRepository.save(event);

        //then
        assertThat(savedEvent.getId()).isNotNull();
        assertThat(savedEvent.getEventKey()).isEqualTo("test-key-1");
    }

    @Test
    @DisplayName("eventKey로 InboxEvent를 조회할 수 있다.")
    void findByEventKey_success() {
        //given
        InboxEvent event = createInboxEvent("test-key-2");
        testEntityManager.persistAndFlush(event);

        //when
        Optional<InboxEvent> foundEvent = jpaInboxRepository.findByEventKey("test-key-2");

        //then
        assertThat(foundEvent).isPresent();
        assertThat(foundEvent.get().getEventKey()).isEqualTo("test-key-2");
    }

    @Test
    @DisplayName("eventKey 존재 여부를 확인할 수 있다.")
    void existsByEventKey_returnsTrue() {
        //given
        InboxEvent event = createInboxEvent("test-key-3");
        testEntityManager.persistAndFlush(event);

        //when
        boolean exists = jpaInboxRepository.existsByEventKey("test-key-3");

        //then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 eventKey는 false를 반환한다.")
    void existsByEventKey_returnsFalse() {
        //when
        boolean exists = jpaInboxRepository.existsByEventKey("non-existent-key");

        //then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("상태와 수정시간으로 InboxEvent를 조회할 수 있다.")
    void findByStatusAndModifiedAtBefore_success() {
        //given
        InboxEvent oldEvent = createInboxEvent("old-key");
        oldEvent.updateStatus(InboxStatus.PROCESSING);
        testEntityManager.persistAndFlush(oldEvent);

        InboxEvent recentEvent = createInboxEvent("recent-key");
        recentEvent.updateStatus(InboxStatus.PROCESSING);
        testEntityManager.persistAndFlush(recentEvent);

        LocalDateTime threshold = LocalDateTime.now().plusMinutes(1);

        //when
        List<InboxEvent> events = jpaInboxRepository.findByStatusAndModifiedAtBefore(
                InboxStatus.PROCESSING, threshold
        );

        //then
        assertThat(events).hasSize(2);
    }

    @Test
    @DisplayName("상태별 InboxEvent 개수를 조회할 수 있다.")
    void countByStatus_success() {
        //given
        InboxEvent event1 = createInboxEvent("key-1");
        event1.updateStatus(InboxStatus.COMPLETED);
        testEntityManager.persistAndFlush(event1);

        InboxEvent event2 = createInboxEvent("key-2");
        event2.updateStatus(InboxStatus.COMPLETED);
        testEntityManager.persistAndFlush(event2);

        InboxEvent event3 = createInboxEvent("key-3");
        event3.updateStatus(InboxStatus.FAILED);
        testEntityManager.persistAndFlush(event3);

        //when
        long completedCount = jpaInboxRepository.countByStatus(InboxStatus.COMPLETED);
        long failedCount = jpaInboxRepository.countByStatus(InboxStatus.FAILED);

        //then
        assertThat(completedCount).isEqualTo(2);
        assertThat(failedCount).isEqualTo(1);
    }

    private static InboxEvent createInboxEvent(String eventKey) {
        return InboxEvent.builder()
                .eventKey(eventKey)
                .eventName("ORDER_CANCEL_EVENT")
                .payload("{\"orderId\":\"123\"}")
                .status(InboxStatus.RECEIVED)
                .receivedAt(LocalDateTime.now())
                .retryCount(0)
                .maxRetries(3)
                .build();
    }
}
