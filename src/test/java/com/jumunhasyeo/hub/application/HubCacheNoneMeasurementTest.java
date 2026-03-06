package com.jumunhasyeo.hub.application;

import com.jumunhasyeo.hub.hub.application.HubService;
import com.jumunhasyeo.hub.hub.application.HubServiceImpl;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.TestPropertySource;

@EnabledIfSystemProperty(named = "hubCache.measurement.enabled", matches = "true")
@TestPropertySource(properties = "cache.config.hubService=NONE")
class HubCacheNoneMeasurementTest extends AbstractHubCacheComparisonMeasurementTest {

    @Override
    protected String cacheMode() {
        return "NONE";
    }

    @Override
    protected Class<? extends HubService> expectedServiceType() {
        return HubServiceImpl.class;
    }
}
