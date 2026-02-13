package com.jumunhasyeo.hub.infrastructure.outbox;

import com.jumunhasyeo.testsupport.RepositorySliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpaOutboxRepositorySliceTest extends RepositorySliceTest {

    @Autowired
    private JpaOutboxRepository jpaOutboxRepository;

    @Test
    @DisplayName("OutboxEvent를 저장할 수 있다.")
    void save_OutboxEvent_success() {
        //given
        OutboxEvent event = createOutboxEvent("test-key-1");

        //when
        OutboxEvent savedEvent = jpaOutboxRepository.save(event);

        //then
        assertThat(savedEvent.getId()).isNotNull();
        assertThat(savedEvent.getEventKey()).isEqualTo("test-key-1");
    }

    @Test
    @DisplayName("eventKey로 OutboxEvent를 조회할 수 있다.")
    void findByEventKey_success() {
        //given
        OutboxEvent event = createOutboxEvent("test-key-2");
        testEntityManager.persistAndFlush(event);

        //when
        Optional<OutboxEvent> foundEvent = jpaOutboxRepository.findByEventKey("test-key-2");

        //then
        assertThat(foundEvent).isPresent();
        assertThat(foundEvent.get().getEventKey()).isEqualTo("test-key-2");
    }

    @Test
    @DisplayName("Outbox eventKey는 중복 저장할 수 없다.")
    void save_WhenEventKeyDuplicated_throwsException() {
        // given
        OutboxEvent event = createOutboxEvent("duplicated-key");
        OutboxEvent duplicatedEvent = createOutboxEvent("duplicated-key");
        testEntityManager.persistAndFlush(event);

        // when & then
        assertThatThrownBy(() -> jpaOutboxRepository.saveAndFlush(duplicatedEvent))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("존재하지 않는 eventKey는 빈 Optional을 반환한다.")
    void findByEventKey_NotFound_returnsEmpty() {
        //when
        Optional<OutboxEvent> foundEvent = jpaOutboxRepository.findByEventKey("non-existent-key");

        //then
        assertThat(foundEvent).isEmpty();
    }

    @Test
    @DisplayName("PENDING 상태의 이벤트 상위 100개를 조회할 수 있다.")
    void findTop100ByStatusOrderByIdAsc_success() {
        //given
        for (int i = 0; i < 5; i++) {
            OutboxEvent event = createOutboxEvent("key-" + i);
            testEntityManager.persistAndFlush(event);
        }

        //when
        List<OutboxEvent> events = jpaOutboxRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING);

        //then
        assertThat(events).hasSize(5);
    }

    @Test
    @DisplayName("특정 상태의 오래된 이벤트를 삭제할 수 있다.")
    void deleteByStatusAndCreatedAtBefore_success() {
        //given
        OutboxEvent event1 = createOutboxEvent("key-1");
        event1.markProcessed();
        testEntityManager.persistAndFlush(event1);

        OutboxEvent event2 = createOutboxEvent("key-2");
        event2.markProcessed();
        testEntityManager.persistAndFlush(event2);

        OutboxEvent event3 = createOutboxEvent("key-3");
        testEntityManager.persistAndFlush(event3);

        testEntityManager.flush();
        testEntityManager.clear();

        LocalDateTime cutoff = LocalDateTime.now().plusMinutes(1);

        //when
        int deletedCount = jpaOutboxRepository.deleteByStatusAndCreatedAtBefore(
                OutboxStatus.COMPLETE, cutoff
        );

        //then
        assertThat(deletedCount).isEqualTo(2);
    }

    @Test
    @DisplayName("다른 상태의 이벤트는 삭제되지 않는다.")
    void deleteByStatusAndCreatedAtBefore_OnlyDeletesMatchingStatus() {
        //given
        OutboxEvent completedEvent = createOutboxEvent("completed-key");
        completedEvent.markProcessed();
        testEntityManager.persistAndFlush(completedEvent);

        OutboxEvent pendingEvent = createOutboxEvent("pending-key");
        testEntityManager.persistAndFlush(pendingEvent);

        testEntityManager.flush();
        testEntityManager.clear();

        LocalDateTime cutoff = LocalDateTime.now().plusMinutes(1);

        //when
        int deletedCount = jpaOutboxRepository.deleteByStatusAndCreatedAtBefore(
                OutboxStatus.COMPLETE, cutoff
        );

        //then
        assertThat(deletedCount).isEqualTo(1);
        Optional<OutboxEvent> remainingEvent = jpaOutboxRepository.findByEventKey("pending-key");
        assertThat(remainingEvent).isPresent();
    }

    private static OutboxEvent createOutboxEvent(String eventKey) {
        return OutboxEvent.of(
                "HubCreatedEvent",
                "{\"hubId\":\"123\"}",
                eventKey,
                "hub"
        );
    }
}
