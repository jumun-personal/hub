package com.jumunhasyeo.hub.hubRoute.infrastructure.external.client.map;

import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import feign.Logger;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MapApiFeignClientConfig {
    @Bean(name = "kakaoFeignLoggerLevel")
    Logger.Level kakaoFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean(name = "kakaoErrorDecoder")
    ErrorDecoder kakaoErrorDecoder() {
        return (methodKey, response) -> {
            log.error("Kakao Mobility API Error: {} - {}",
                    response.status(), response.reason());

            return RouteApiErrorDecoderSupport.decode(MapProvider.KAKAO, response);
        };
    }
}
