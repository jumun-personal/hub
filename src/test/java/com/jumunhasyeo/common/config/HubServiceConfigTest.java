package com.jumunhasyeo.common.config;

import com.jumunhasyeo.hub.hub.application.HubEventPublisher;
import com.jumunhasyeo.hub.hub.application.HubRedisCachedDecoratorService;
import com.jumunhasyeo.hub.hub.application.HubService;
import com.jumunhasyeo.hub.hub.application.HubServiceImpl;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepositoryCustom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("HubServiceConfig 빈 주입 테스트")
class HubServiceConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(HubServiceConfig.class, MockHubDependenciesConfig.class)
            .withPropertyValues("dynamic.enabled=false");

    @Test
    @DisplayName("REDIS 캐시 설정 시 HubRedisCachedDecoratorService가 주입된다")
    void shouldInjectRedisCachedDecoratorService() {
        contextRunner
                .withPropertyValues("cache.config.hubService=REDIS")
                .run(context -> {
                    assertThat(context).hasSingleBean(HubService.class);
                    assertThat(context.getBean(HubService.class))
                            .isInstanceOf(HubRedisCachedDecoratorService.class);
                });
    }

    @Test
    @DisplayName("NONE 설정 시 HubServiceImpl이 직접 주입된다")
    void shouldInjectHubServiceImpl() {
        contextRunner
                .withPropertyValues("cache.config.hubService=NONE")
                .run(context -> {
                    assertThat(context).hasSingleBean(HubService.class);
                    assertThat(context.getBean(HubService.class))
                            .isInstanceOf(HubServiceImpl.class);
                });
    }

    @Test
    @DisplayName("설정이 없을 때 기본값으로 HubRedisCachedDecoratorService가 주입된다")
    void shouldInjectDefaultRedisCachedDecoratorService() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(HubService.class);
                    assertThat(context.getBean(HubService.class))
                            .isInstanceOf(HubRedisCachedDecoratorService.class);
                });
    }

    @Test
    @DisplayName("잘못된 설정값이면 폴백으로 HubRedisCachedDecoratorService가 주입된다")
    void shouldFallbackToRedisCachedDecoratorService() {
        contextRunner
                .withPropertyValues("cache.config.hubService=INVALID_VALUE")
                .run(context -> {
                    assertThat(context).hasSingleBean(HubService.class);
                    assertThat(context.getBean(HubService.class))
                            .isInstanceOf(HubRedisCachedDecoratorService.class);
                });
    }

    @TestConfiguration
    static class MockHubDependenciesConfig {
        @Bean
        HubRepository hubRepository() {
            return mock(HubRepository.class);
        }

        @Bean
        HubRepositoryCustom hubRepositoryCustom() {
            return mock(HubRepositoryCustom.class);
        }

        @Bean
        HubEventPublisher hubEventPublisher() {
            return mock(HubEventPublisher.class);
        }
    }
}
