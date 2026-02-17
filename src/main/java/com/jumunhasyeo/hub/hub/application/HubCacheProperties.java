package com.jumunhasyeo.hub.hub.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "hub.cache")
public class HubCacheProperties {
    private Duration ttl = Duration.ofMinutes(30);
    private Duration lockTtl = Duration.ofSeconds(3);
    private Duration waitInterval = Duration.ofMillis(50);
    private int maxWaitAttempts = 10;
    private int dbFallbackBulkheadSize = 20;
}
