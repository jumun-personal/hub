package com.jumunhasyeo.hub.hub.infrastructure.dynamic;

import com.jumunhasyeo.hub.hub.application.HubEventPublisher;
import com.jumunhasyeo.hub.hub.application.HubCacheProperties;
import com.jumunhasyeo.hub.hub.application.HubRedisCachedDecoratorService;
import com.jumunhasyeo.hub.hub.application.HubServiceImpl;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepositoryCustom;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderAvailabilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "dynamic.enabled", havingValue = "true")
public class HubDynamicServiceConfig {

    @Bean
    public HubServiceImpl hubServiceImpl(
            HubRepository hubRepository,
            HubRepositoryCustom hubRepositoryCustom,
            HubEventPublisher hubEventPublisher,
            RouteProviderAvailabilityService routeProviderAvailabilityService
    ) {
        log.info("[Dynamic] Creating HubServiceImpl");
        return new HubServiceImpl(
                hubRepository,
                hubRepositoryCustom,
                hubEventPublisher,
                routeProviderAvailabilityService
        );
    }

    @Bean
    public HubRedisCachedDecoratorService hubRedisCached(
            HubServiceImpl hubServiceImpl,
            RedisTemplate<String, Object> redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            HubCacheProperties hubCacheProperties
    ) {
        log.info("[Dynamic] Creating HubRedisCachedDecoratorService");
        return new HubRedisCachedDecoratorService(
                hubServiceImpl,
                redisTemplate,
                stringRedisTemplate,
                hubCacheProperties
        );
    }
}
