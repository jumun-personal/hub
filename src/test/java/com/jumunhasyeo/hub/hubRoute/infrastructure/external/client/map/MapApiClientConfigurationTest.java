package com.jumunhasyeo.hub.hubRoute.infrastructure.external.client.map;

import feign.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("지도 API Feign 설정")
class MapApiClientConfigurationTest {

    private static final String KAKAO_CLIENT = "kakao-mobility-client";
    private static final String NAVER_CLIENT = "naver-map-client";

    @Test
    @DisplayName("운영 예시와 테스트 설정은 지도 API 연결 1초·응답 4초를 동일하게 사용한다")
    void configuredTimeoutsMatchMeasuredInitialValues() {
        // given
        List<String> configurationFiles = List.of("application-sample.yml", "application-test.yml");

        // when
        List<FeignClientProperties> propertiesByFile = configurationFiles.stream()
                .map(this::loadConfigurationUnchecked)
                .toList();

        // then
        for (FeignClientProperties properties : propertiesByFile) {
            assertTimeout(properties, KAKAO_CLIENT);
            assertTimeout(properties, NAVER_CLIENT);
        }
    }

    @Test
    @DisplayName("지도 API 로그는 인증 헤더와 본문을 기록하지 않는 BASIC 수준을 사용한다")
    void loggerLevelDoesNotExposeHeadersOrBodies() {
        // given
        MapApiFeignClientConfig kakaoConfig = new MapApiFeignClientConfig();
        NaverMapFeignClientConfig naverConfig = new NaverMapFeignClientConfig();

        // when
        Logger.Level kakaoLevel = kakaoConfig.kakaoFeignLoggerLevel();
        Logger.Level naverLevel = naverConfig.naverFeignLoggerLevel();

        // then
        assertThat(kakaoLevel).isEqualTo(Logger.Level.BASIC);
        assertThat(naverLevel).isEqualTo(Logger.Level.BASIC);
    }

    private FeignClientProperties loadConfiguration(String configurationFile) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(
                configurationFile,
                new ClassPathResource(configurationFile)
        );
        propertySources.forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment)
                .bind("spring.cloud.openfeign.client", Bindable.of(FeignClientProperties.class))
                .orElseThrow(() -> new IllegalStateException(
                        configurationFile + "에서 지도 API Feign 설정을 불러올 수 없습니다."
                ));
    }

    private FeignClientProperties loadConfigurationUnchecked(String configurationFile) {
        try {
            return loadConfiguration(configurationFile);
        } catch (IOException exception) {
            throw new IllegalStateException(configurationFile + "을 읽을 수 없습니다.", exception);
        }
    }

    private void assertTimeout(FeignClientProperties properties, String clientName) {
        FeignClientProperties.FeignClientConfiguration configuration =
                properties.getConfig().get(clientName);
        assertThat(configuration).isNotNull();
        assertThat(configuration.getConnectTimeout()).isEqualTo(1_000);
        assertThat(configuration.getReadTimeout()).isEqualTo(4_000);
    }
}
