package com.jumunhasyeo.common.dynamic;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 동적 서비스 구현체 설정
 * 
 * 런타임에 API로 변경 가능
 */
@Data
@Component
@ConfigurationProperties(prefix = "dynamic")
public class DynamicConfig {
    
    /**
     * HubService 캐시 타입
     * REDIS | NONE
     */
    private String hubCache = HubServiceCacheType.REDIS.name();

    /**
     * StockVariationService 구현체 타입
     * DEFAULT | PESSIMISTIC_LOCK
     */
    private String stockLock = StockLockType.DEFAULT.name();
}
