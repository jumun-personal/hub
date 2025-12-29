package com.jumunhasyeo.common.config;

import com.jumunhasyeo.hub.hub.application.HubEventPublisher;
import com.jumunhasyeo.hub.hub.application.HubRedisCachedDecoratorService;
import com.jumunhasyeo.hub.hub.application.HubService;
import com.jumunhasyeo.hub.hub.application.HubServiceImpl;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepositoryCustom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HubService 빈 설정
 */
@Configuration
@Slf4j
@ConditionalOnProperty(name = "dynamic.enabled", havingValue = "false", matchIfMissing = true)
public class HubServiceConfig {

    @Bean
    @ConditionalOnProperty(name = "cache.config.hubService", havingValue = "REDIS")
    public HubService hubServiceRedis(
            HubRepository hubRepository,
            HubRepositoryCustom hubRepositoryCustom,
            HubEventPublisher hubEventPublisher
    ) {
        log.info("[FixedCache] Creating HubService with Redis");
        HubServiceImpl impl = new HubServiceImpl(hubRepository, hubRepositoryCustom, hubEventPublisher);
        return new HubRedisCachedDecoratorService(impl);
    }

    @Bean
    @ConditionalOnProperty(name = "cache.config.hubService", havingValue = "NONE")
    public HubService hubServiceNone(
            HubRepository hubRepository,
            HubRepositoryCustom hubRepositoryCustom,
            HubEventPublisher hubEventPublisher
    ) {
        log.info("[FixedCache] Creating HubService without cache");
        return new HubServiceImpl(hubRepository, hubRepositoryCustom, hubEventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean(HubService.class)
    public HubService hubServiceDefault(
            HubRepository hubRepository,
            HubRepositoryCustom hubRepositoryCustom,
            HubEventPublisher hubEventPublisher
    ) {
        log.warn("[FixedCache] Fallback - Creating HubService with Redis");
        HubServiceImpl impl = new HubServiceImpl(hubRepository, hubRepositoryCustom, hubEventPublisher);
        return new HubRedisCachedDecoratorService(impl);
    }
}
