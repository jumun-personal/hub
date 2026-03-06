package com.jumunhasyeo;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class CommonTestContainer {

    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER;
    private static final GenericContainer<?> REDIS_CONTAINER;
    private static final GenericContainer<?> REDIS_BF_CONTAINER;
    private static final KafkaContainer KAFKA_CONTAINER;

    static {
        POSTGRES_CONTAINER = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass")
                .withCommand("postgres", "-c", "max_connections=500")
                .withReuse(true);

        REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withReuse(true);

        REDIS_BF_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withReuse(true);

        KAFKA_CONTAINER = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                .withReuse(true);

        System.out.println("Starting PostgreSQL container...");
        POSTGRES_CONTAINER.start();
        System.out.println("PostgreSQL container started: " + POSTGRES_CONTAINER.getJdbcUrl());

        System.out.println("Starting Redis container...");
        REDIS_CONTAINER.start();
        REDIS_BF_CONTAINER.start();
        System.out.println("Redis container started at: " + REDIS_CONTAINER.getHost() + ":" + REDIS_CONTAINER.getMappedPort(6379));

        System.out.println("Starting Kafka container...");
        KAFKA_CONTAINER.start();
        System.out.println("Kafka container started at: " + KAFKA_CONTAINER.getBootstrapServers());
    }

    @DynamicPropertySource
    public static void overrideProps(DynamicPropertyRegistry registry) {
        // PostgreSQL 설정
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // JPA 설정
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.show-sql", () -> System.getProperty("test.jpa.showSql", "true"));
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");

        // Redis 설정
        registry.add("spring.data.redis.cache.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.cache.port", () -> REDIS_CONTAINER.getMappedPort(6379).toString());
        registry.add("spring.data.redis.ssl.enabled", () -> "false");

        registry.add("spring.data.redis.black-friday.host", REDIS_BF_CONTAINER::getHost);
        registry.add("spring.data.redis.black-friday.port", () -> REDIS_BF_CONTAINER.getMappedPort(6379).toString());
        registry.add("spring.data.redis.ssl.enabled", () -> "false");

        // Kafka 설정
        registry.add("spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.group-id", () -> "test-group");
    }
}
