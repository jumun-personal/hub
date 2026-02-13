package com.jumunhasyeo.common.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import com.fasterxml.jackson.core.JsonProcessingException;

import com.jumunhasyeo.hub.hubRoute.infrastructure.event.HubRouteDlqRecoverer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private static final String TRANSACTION_ID_PREFIX = "hub-route-tx-";

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000); // 메타데이터 조회 대기 시간
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5000); // 전송 타임아웃
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000); // 요청 타임아웃
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        DefaultKafkaProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(configProps);
        producerFactory.setTransactionIdPrefix(TRANSACTION_ID_PREFIX);
        return producerFactory;
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory());
        kafkaTemplate.setAllowNonTransactional(true);
        return kafkaTemplate;
    }

    @Bean
    public KafkaTransactionManager<String, String> kafkaTransactionManager(
            ProducerFactory<String, String> producerFactory
    ) {
        return new KafkaTransactionManager<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public DefaultErrorHandler defaultErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        return buildDefaultErrorHandler(deadLetterPublishingRecoverer(kafkaTemplate));
    }

    @Bean
    public DefaultErrorHandler stockKafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        return buildDefaultErrorHandler(deadLetterPublishingRecoverer(kafkaTemplate));
    }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            @Qualifier("defaultErrorHandler") DefaultErrorHandler defaultErrorHandler
    ) {
        return buildListenerContainerFactory(
                consumerFactory,
                defaultErrorHandler,
                ContainerProperties.AckMode.RECORD
        );
    }

    @Bean(name = "stockKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> stockKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            @Qualifier("stockKafkaErrorHandler") DefaultErrorHandler stockKafkaErrorHandler
    ) {
        return buildListenerContainerFactory(
                consumerFactory,
                stockKafkaErrorHandler,
                ContainerProperties.AckMode.BATCH
        );
    }

    @Bean(name = "hubRouteKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> hubRouteKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            HubRouteDlqRecoverer hubRouteDlqRecoverer,
            KafkaTransactionManager<String, String> kafkaTransactionManager
    ) {
        return buildListenerContainerFactory(
                consumerFactory,
                hubRouteDlqRecoverer.buildErrorHandler(),
                ContainerProperties.AckMode.RECORD,
                kafkaTransactionManager
        );
    }

    private ConcurrentKafkaListenerContainerFactory<String, String> buildListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler errorHandler,
            ContainerProperties.AckMode ackMode
    ) {
        return buildListenerContainerFactory(consumerFactory, errorHandler, ackMode, null);
    }

    private ConcurrentKafkaListenerContainerFactory<String, String> buildListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler errorHandler,
            ContainerProperties.AckMode ackMode,
            KafkaTransactionManager<String, String> kafkaTransactionManager
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ackMode);
        factory.setCommonErrorHandler(errorHandler);
        if (kafkaTransactionManager != null) {
            factory.getContainerProperties().setKafkaAwareTransactionManager(kafkaTransactionManager);
        }
        return factory;
    }

    private DefaultErrorHandler buildDefaultErrorHandler(ConsumerRecordRecoverer recoverer) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, kafkaBackOff());
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class);
        return errorHandler;
    }

    private DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, String> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition())
        );
    }

    private ExponentialBackOffWithMaxRetries kafkaBackOff() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(2);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(2000L);
        return backOff;
    }
}
