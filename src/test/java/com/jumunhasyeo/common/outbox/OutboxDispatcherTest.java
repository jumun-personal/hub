package com.jumunhasyeo.hub.infrastructure.outbox;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Spy
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @InjectMocks
    private OutboxDispatcher outboxDispatcher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxDispatcher, "hubTopic", "hub-topic");
    }

    @Test
    @DisplayName("알 수 없는 토픽이면 INVALID_INPUT 예외가 발생한다.")
    void dispatch_whenUnknownTopic_throwsBusinessException() {
        OutboxEvent event = OutboxEvent.of("HubCreatedEvent", "{}", "event-key", "unknown-topic");

        assertThatThrownBy(() -> outboxDispatcher.dispatch(event))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("Kafka 발행 시 payload의 hubId를 partition key로 사용한다.")
    @SuppressWarnings("unchecked")
    void dispatch_usesHubIdAsPartitionKey() throws Exception {
        String hubId = "550e8400-e29b-41d4-a716-446655440000";
        OutboxEvent event = OutboxEvent.of(
                "HubRouteCreatedEvent",
                "{\"hubId\":\"" + hubId + "\",\"startHub\":\"start\"}",
                "event-key",
                "hub-topic"
        );
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(future);

        outboxDispatcher.dispatch(event);

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        then(kafkaTemplate).should().send(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo(hubId);
    }

    @Test
    @DisplayName("경로 생성 이벤트와 완료 이벤트는 같은 hubId를 partition key로 사용한다.")
    @SuppressWarnings("unchecked")
    void dispatch_routeCreatedAndBuildCompleted_useSameHubIdPartitionKey() throws Exception {
        // given
        String hubId = "550e8400-e29b-41d4-a716-446655440000";
        OutboxEvent routeCreated = OutboxEvent.of(
                "HubRouteCreatedEvent",
                "{\"hubId\":\"" + hubId + "\",\"routeId\":\"route-id\"}",
                "created-event-key",
                "hub-topic"
        );
        OutboxEvent buildCompleted = OutboxEvent.of(
                "HubRouteBuildCompletedEvent",
                "{\"hubId\":\"" + hubId + "\",\"hubType\":\"CENTER\"}",
                "completed-event-key",
                "hub-topic"
        );
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(future);

        // when
        outboxDispatcher.dispatch(routeCreated);
        outboxDispatcher.dispatch(buildCompleted);

        // then
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        then(kafkaTemplate).should(times(2)).send(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ProducerRecord::key)
                .containsExactly(hubId, hubId);
    }

    @Test
    @DisplayName("Kafka 전송 실패면 INTERNAL_SERVER_ERROR 예외가 발생한다.")
    @SuppressWarnings("unchecked")
    void dispatch_whenKafkaSendFails_throwsBusinessException() throws Exception {
        OutboxEvent event = OutboxEvent.of("HubCreatedEvent", "{}", "event-key", "hub-topic");
        CompletableFuture<SendResult<String, String>> future = org.mockito.Mockito.mock(CompletableFuture.class);
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(future);
        given(future.get()).willThrow(new ExecutionException(new RuntimeException("kafka send fail")));

        assertThatThrownBy(() -> outboxDispatcher.dispatch(event))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR);
    }

}
