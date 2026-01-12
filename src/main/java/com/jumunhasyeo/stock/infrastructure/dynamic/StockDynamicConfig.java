package com.jumunhasyeo.stock.infrastructure.dynamic;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dynamic")
public class StockDynamicConfig {
    private String stockLock = StockLockType.DEFAULT.name();
}
