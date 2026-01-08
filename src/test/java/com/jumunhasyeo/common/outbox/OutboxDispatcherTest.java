package com.jumunhasyeo.common.outbox;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

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
