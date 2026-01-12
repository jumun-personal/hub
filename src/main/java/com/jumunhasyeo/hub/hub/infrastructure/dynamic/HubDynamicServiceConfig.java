package com.jumunhasyeo.hub.hub.infrastructure.dynamic;

import com.jumunhasyeo.hub.hub.application.HubEventPublisher;
import com.jumunhasyeo.hub.hub.application.HubRedisCachedDecoratorService;
import com.jumunhasyeo.hub.hub.application.HubServiceImpl;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepositoryCustom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "dynamic.enabled", havingValue = "true")
public class HubDynamicServiceConfig {

    @Bean
    public HubServiceImpl hubServiceImpl(
            HubRepository hubRepository,
            HubRepositoryCustom hubRepositoryCustom,
            HubEventPublisher hubEventPublisher
    ) {
        log.info("[Dynamic] Creating HubServiceImpl");
        return new HubServiceImpl(hubRepository, hubRepositoryCustom, hubEventPublisher);
    }

    @Bean
    public HubRedisCachedDecoratorService hubRedisCached(HubServiceImpl hubServiceImpl) {
        log.info("[Dynamic] Creating HubRedisCachedDecoratorService");
        return new HubRedisCachedDecoratorService(hubServiceImpl);
    }
}
