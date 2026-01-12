package com.jumunhasyeo.hub.hub.infrastructure.dynamic;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dynamic")
public class HubDynamicConfig {
    private String hubCache = HubServiceCacheType.REDIS.name();
}
