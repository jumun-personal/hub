package com.jumunhasyeo.common.config;

import com.jumunhasyeo.hub.hub.application.HubEventPublisher;
import com.jumunhasyeo.hub.hub.application.BenchmarkSyncRouteBuildHubService;
import com.jumunhasyeo.hub.hub.application.HubCacheProperties;
import com.jumunhasyeo.hub.hub.application.HubRedisCachedDecoratorService;
import com.jumunhasyeo.hub.hub.application.HubService;
import com.jumunhasyeo.hub.hub.application.HubServiceImpl;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepositoryCustom;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteBuildJobService;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderResolution;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import com.jumunhasyeo.hub.hubRoute.domain.service.HubRouteDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
            HubRouteRepository hubRouteRepository,
            HubRouteDomainService hubRouteDomainService,
            RouteProviderResolution routeProviderResolution,
            RedisTemplate<String, Object> redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            HubCacheProperties hubCacheProperties,
            @Value("${benchmark.sync-route-build.enabled:false}") boolean benchmarkSyncRouteBuildEnabled
    ) {
        log.info("[FixedCache] Creating HubService with Redis");
        HubService impl = createHubService(
                hubRepository,
                hubRepositoryCustom,
                hubEventPublisher,
                hubRouteService,
                hubRouteBuildJobService,
                hubRouteRepository,
                hubRouteDomainService,
                routeProviderResolution,
                benchmarkSyncRouteBuildEnabled
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
            HubRouteBuildJobService hubRouteBuildJobService,
            HubRouteRepository hubRouteRepository,
            HubRouteDomainService hubRouteDomainService,
            RouteProviderResolution routeProviderResolution,
            @Value("${benchmark.sync-route-build.enabled:false}") boolean benchmarkSyncRouteBuildEnabled
    ) {
        log.info("[FixedCache] Creating HubService without cache");
        return createHubService(
                hubRepository,
                hubRepositoryCustom,
                hubEventPublisher,
                hubRouteService,
                hubRouteBuildJobService,
                hubRouteRepository,
                hubRouteDomainService,
                routeProviderResolution,
                benchmarkSyncRouteBuildEnabled
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
            HubRouteRepository hubRouteRepository,
            HubRouteDomainService hubRouteDomainService,
            RouteProviderResolution routeProviderResolution,
            RedisTemplate<String, Object> redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            HubCacheProperties hubCacheProperties,
            @Value("${benchmark.sync-route-build.enabled:false}") boolean benchmarkSyncRouteBuildEnabled
    ) {
        log.warn("[FixedCache] Fallback - Creating HubService with Redis");
        HubService impl = createHubService(
                hubRepository,
                hubRepositoryCustom,
                hubEventPublisher,
                hubRouteService,
                hubRouteBuildJobService,
                hubRouteRepository,
                hubRouteDomainService,
                routeProviderResolution,
                benchmarkSyncRouteBuildEnabled
        );
        return new HubRedisCachedDecoratorService(
                impl,
                redisTemplate,
                stringRedisTemplate,
                hubCacheProperties
        );
    }

    private HubService createHubService(
            HubRepository hubRepository,
            HubRepositoryCustom hubRepositoryCustom,
            HubEventPublisher hubEventPublisher,
            HubRouteService hubRouteService,
            HubRouteBuildJobService hubRouteBuildJobService,
            HubRouteRepository hubRouteRepository,
            HubRouteDomainService hubRouteDomainService,
            RouteProviderResolution routeProviderResolution,
            boolean benchmarkSyncRouteBuildEnabled
    ) {
        if (benchmarkSyncRouteBuildEnabled) {
            log.warn("[Benchmark] Creating HubService with synchronous route build");
            return new BenchmarkSyncRouteBuildHubService(
                    hubRepository,
                    hubRepositoryCustom,
                    hubEventPublisher,
                    hubRouteService,
                    hubRouteBuildJobService,
                    hubRouteRepository,
                    hubRouteDomainService,
                    routeProviderResolution
            );
        }
        return new HubServiceImpl(
                hubRepository,
                hubRepositoryCustom,
                hubEventPublisher,
                hubRouteService,
                hubRouteBuildJobService
        );
    }
}
