package com.jumunhasyeo.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    @Primary
    public RedisConnectionFactory cacheRedisConnectionFactory(
            @Value("${spring.data.redis.cache.host}") String host,
            @Value("${spring.data.redis.cache.port}") int port
    ) {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(host);
        redisConfig.setPort(port);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    public RedisConnectionFactory bfRedisConnectionFactory(
            @Value("${spring.data.redis.black-friday.host}") String host,
            @Value("${spring.data.redis.black-friday.port}") int port
    ) {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(host);
        redisConfig.setPort(port);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(@Qualifier("cacheRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(@Qualifier("cacheRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisTemplate<String, Object> bfRedisTemplate(@Qualifier("bfRedisConnectionFactory") RedisConnectionFactory bfRedisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(bfRedisConnectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @Primary
    public RedisCacheManager cacheManager(
            RedisConnectionFactory factory,
            @Value("${hub.cache.ttl:30m}") Duration hubCacheTtl
    ) {
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer())
                );

        // 캐시별로 다른 TTL TODO 나중에 설정 파일로 분리
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("hub", cacheConfig.entryTtl(hubCacheTtl)); // 허브 단건/전체 조회
        cacheConfigs.put("product", cacheConfig.entryTtl(Duration.ofMinutes(30))); // 상품 단건 조회 30분

        return RedisCacheManager.builder(factory)
                .cacheDefaults(cacheConfig)
                .withInitialCacheConfigurations(cacheConfigs) // 캐시별 설정 적용
                .build();
    }
}
