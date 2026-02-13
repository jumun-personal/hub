package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RouteProviderAvailabilityServiceTest {

    private static final String PROVIDERS_UNAVAILABLE_KEY = "hub-route:providers:unavailable";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RouteProviderAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new RouteProviderAvailabilityService(stringRedisTemplate);
        ReflectionTestUtils.setField(service, "providerUnavailableTtl", "30s");
    }

    @Test
    @DisplayName("모든 지도 Provider가 실패하면 Redis에 허브 생성 차단 상태를 TTL과 함께 저장한다")
    void markAllProvidersUnavailable_savesRedisStateWithTtl() {
        // given
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        service.markAllProvidersUnavailable("map providers down");

        // then
        then(valueOperations).should().set(
                PROVIDERS_UNAVAILABLE_KEY,
                "map providers down",
                Duration.ofSeconds(30)
        );
    }

    @Test
    @DisplayName("Redis에 지도 Provider 실패 상태가 있으면 허브 생성을 차단한다")
    void assertRouteCreationAvailable_whenUnavailableStateExists_throwsException() {
        // given
        given(stringRedisTemplate.hasKey(PROVIDERS_UNAVAILABLE_KEY)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> service.assertRouteCreationAvailable())
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.HUB_ROUTE_PROVIDER_UNAVAILABLE));
    }

    @Test
    @DisplayName("Redis에 지도 Provider 실패 상태가 없으면 허브 생성을 허용한다")
    void assertRouteCreationAvailable_whenUnavailableStateMissing_allowsCreation() {
        // given
        given(stringRedisTemplate.hasKey(PROVIDERS_UNAVAILABLE_KEY)).willReturn(false);

        // when & then
        assertThatCode(() -> service.assertRouteCreationAvailable())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis 조회 실패 시 허브 생성 차단은 fail-open으로 처리한다")
    void assertRouteCreationAvailable_whenRedisReadFails_allowsCreation() {
        // given
        given(stringRedisTemplate.hasKey(PROVIDERS_UNAVAILABLE_KEY))
                .willThrow(new RuntimeException("redis down"));

        // when & then
        assertThatCode(() -> service.assertRouteCreationAvailable())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("지도 Provider가 회복되면 Redis 차단 상태를 삭제한다")
    void clearAllProvidersUnavailable_deletesRedisState() {
        // when
        service.clearAllProvidersUnavailable();

        // then
        then(stringRedisTemplate).should().delete(PROVIDERS_UNAVAILABLE_KEY);
    }
}
