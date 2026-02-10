package com.jumunhasyeo.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jumunhasyeo.hub.hub.infrastructure.event.HubDlqKafkaEventListener;
import com.jumunhasyeo.hub.hub.infrastructure.event.HubKafkaEventListener;
import com.jumunhasyeo.hub.hubRoute.infrastructure.event.HubRouteDlqRecoverer;
import com.jumunhasyeo.hub.hubRoute.infrastructure.event.HubRouteKafkaEventListener;
import com.jumunhasyeo.stock.infrastructure.event.KafkaStockEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class KafkaConfigTest {

    @Mock
    private ConsumerFactory<String, String> consumerFactory;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private HubRouteDlqRecoverer hubRouteDlqRecoverer;

    private KafkaConfig kafkaConfig;

    @BeforeEach
    void setUp() {
        kafkaConfig = new KafkaConfig();
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:9092");
    }

    @Test
    @DisplayName("재고 listener factory는 BATCH ack mode를 사용한다")
    void stockKafkaListenerContainerFactory_usesBatchAckMode() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler();

        var factory = kafkaConfig.stockKafkaListenerContainerFactory(consumerFactory, errorHandler);

        assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.BATCH);
    }

    @Test
    @DisplayName("공통/허브경로 listener factory는 RECORD ack mode를 유지한다")
    void commonAndHubRouteFactories_keepRecordAckMode() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler();
        given(hubRouteDlqRecoverer.buildErrorHandler()).willReturn(errorHandler);

        var commonFactory = kafkaConfig.kafkaListenerContainerFactory(consumerFactory, errorHandler);
        var hubRouteFactory = kafkaConfig.hubRouteKafkaListenerContainerFactory(consumerFactory, hubRouteDlqRecoverer);

        assertThat(commonFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
        assertThat(hubRouteFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
    }

    @Test
    @DisplayName("리스너별 container factory 설정이 기대한 값으로 유지된다")
    void listeners_useExpectedContainerFactories() throws NoSuchMethodException {
        KafkaListener stockListener = KafkaStockEventListener.class
                .getMethod("listen", String.class, String.class)
                .getAnnotation(KafkaListener.class);
        KafkaListener hubListener = HubKafkaEventListener.class
                .getMethod("listen", String.class, String.class)
                .getAnnotation(KafkaListener.class);
        KafkaListener hubRouteListener = HubRouteKafkaEventListener.class
                .getMethod("listen", String.class, String.class)
                .getAnnotation(KafkaListener.class);
        KafkaListener hubDlqListener = HubDlqKafkaEventListener.class
                .getMethod("listen", String.class, String.class, String.class)
                .getAnnotation(KafkaListener.class);

        assertThat(stockListener.containerFactory()).isEqualTo("stockKafkaListenerContainerFactory");
        assertThat(hubListener.containerFactory()).isEqualTo("kafkaListenerContainerFactory");
        assertThat(hubRouteListener.containerFactory()).isEqualTo("hubRouteKafkaListenerContainerFactory");
        assertThat(hubDlqListener.containerFactory()).isEqualTo("kafkaListenerContainerFactory");
    }

    @Test
    @DisplayName("재고 Kafka error handler는 JSON 파싱 실패를 non-retryable로 분류한다")
    void stockKafkaErrorHandler_marksJsonProcessingExceptionAsNotRetryable() {
        DefaultErrorHandler errorHandler = kafkaConfig.stockKafkaErrorHandler(kafkaTemplate);

        BinaryExceptionClassifier classifier = ReflectionTestUtils.invokeMethod(errorHandler, "getClassifier");

        assertThat(classifier.classify(new JsonProcessingException("bad json") {})).isFalse();
    }

    @Test
    @DisplayName("producer factory는 idempotence와 안전한 전송 설정을 사용한다")
    void producerFactory_usesIdempotenceCompatibleSettings() {
        DefaultKafkaProducerFactory<String, String> producerFactory =
                (DefaultKafkaProducerFactory<String, String>) kafkaConfig.producerFactory();

        Map<String, Object> configs = producerFactory.getConfigurationProperties();

        assertThat(configs)
                .containsEntry("enable.idempotence", true)
                .containsEntry("acks", "all")
                .containsEntry("max.in.flight.requests.per.connection", 5);
    }
}
