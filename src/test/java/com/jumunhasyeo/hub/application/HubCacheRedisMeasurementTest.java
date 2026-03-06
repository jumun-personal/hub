package com.jumunhasyeo.hub.application;

import com.jumunhasyeo.hub.hub.application.HubRedisCachedDecoratorService;
import com.jumunhasyeo.hub.hub.application.HubService;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.TestPropertySource;

@EnabledIfSystemProperty(named = "hubCache.measurement.enabled", matches = "true")
@TestPropertySource(properties = "cache.config.hubService=REDIS")
class HubCacheRedisMeasurementTest extends AbstractHubCacheComparisonMeasurementTest {

    @Override
    protected String cacheMode() {
        return "REDIS";
    }

    @Override
    protected Class<? extends HubService> expectedServiceType() {
        return HubRedisCachedDecoratorService.class;
    }
}
