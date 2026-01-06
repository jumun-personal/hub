package com.jumunhasyeo.hub.hubRoute.infrastructure.external.client.map;

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
        return Logger.Level.FULL;
    }

    @Bean(name = "naverErrorDecoder")
    ErrorDecoder naverErrorDecoder() {
        return (methodKey, response) -> {
            log.error("Naver Map API Error: {} - {}", response.status(), response.reason());
            switch (response.status()) {
                case 400:
                    return new IllegalArgumentException("잘못된 요청입니다.");
                case 401:
                case 403:
                    return new IllegalStateException("인증 실패: API 키를 확인하세요.");
                case 429:
                    return new IllegalStateException("API 호출 한도 초과");
                case 500:
                case 502:
                case 503:
                    return new IllegalStateException("Naver 서버 오류");
                default:
                    return new RuntimeException("알 수 없는 오류: " + response.status());
            }
        };
    }
}
