package com.jumunhasyeo.hub.hubRoute.infrastructure.external.client.map;

import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import feign.Logger;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class NaverMapFeignClientConfig {
    @Bean(name = "naverFeignLoggerLevel")
    Logger.Level naverFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean(name = "naverErrorDecoder")
    ErrorDecoder naverErrorDecoder() {
        return (methodKey, response) -> {
            log.error("Naver Map API Error: {} - {}", response.status(), response.reason());
            return RouteApiErrorDecoderSupport.decode(MapProvider.NAVER, response);
        };
    }
}
