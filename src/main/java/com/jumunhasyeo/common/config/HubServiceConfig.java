package com.jumunhasyeo.common.config;

import com.jumunhasyeo.hub.hub.application.HubEventPublisher;
import com.jumunhasyeo.hub.hub.application.HubCacheProperties;
import com.jumunhasyeo.hub.hub.application.HubRedisCachedDecoratorService;
import com.jumunhasyeo.hub.hub.application.HubService;
import com.jumunhasyeo.hub.hub.application.HubServiceImpl;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepositoryCustom;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteBuildJobService;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * HubService 빈 설정
 */
@Configuration
@Slf4j
public class HubServiceConfig {

    @Bean
    @ConditionalOnProperty(name = "cache.config.hubService", havingValue = "REDIS")
    public HubService hubServiceRedis(
            HubRepository hubRepository,
            HubRepositoryCustom hubRepositoryCustom,
            HubEventPublisher hubEventPublisher,
            HubRouteService hubRouteService,
            HubRouteBuildJobService hubRouteBuildJobService,
            RedisTemplate<String, Object> redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            HubCacheProperties hubCacheProperties
    ) {
        log.info("[FixedCache] Creating HubService with Redis");
        HubServiceImpl impl = new HubServiceImpl(
                hubRepository,
                hubRepositoryCustom,
                hubEventPublisher,
                hubRouteService,
                hubRouteBuildJobService
        );
        return new HubRedisCachedDecoratorService(
                impl,
                redisTemplate,
                stringRedisTemplate,
                hubCacheProperties
        );
    }

    @Bean
    @ConditionalOnProperty(name = "cache.config.hubService", havingValue = "NONE")
    public HubService hubServiceNone(
            HubRepository hubRepository,
            HubRepositoryCustom hubRepositoryCustom,
            HubEventPublisher hubEventPublisher,
            HubRouteService hubRouteService,
            HubRouteBuildJobService hubRouteBuildJobService
    ) {
        log.info("[FixedCache] Creating HubService without cache");
        return new HubServiceImpl(
                hubRepository,
                hubRepositoryCustom,
                hubEventPublisher,
                hubRouteService,
                hubRouteBuildJobService
        );
    }

    @Bean
    @ConditionalOnMissingBean(HubService.class)
    public HubService hubServiceDefault(
            HubRepository hubRepository,
            HubRepositoryCustom hubRepositoryCustom,
            HubEventPublisher hubEventPublisher,
            HubRouteService hubRouteService,
            HubRouteBuildJobService hubRouteBuildJobService,
            RedisTemplate<String, Object> redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            HubCacheProperties hubCacheProperties
    ) {
        log.warn("[FixedCache] Fallback - Creating HubService with Redis");
        HubServiceImpl impl = new HubServiceImpl(
                hubRepository,
                hubRepositoryCustom,
                hubEventPublisher,
                hubRouteService,
                hubRouteBuildJobService
        );
        return new HubRedisCachedDecoratorService(
                impl,
                redisTemplate,
                stringRedisTemplate,
                hubCacheProperties
        );
    }

}
