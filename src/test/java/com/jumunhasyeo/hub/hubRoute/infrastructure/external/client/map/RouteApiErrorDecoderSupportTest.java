package com.jumunhasyeo.hub.hubRoute.infrastructure.external.client.map;

import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderConfigurationException;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderTransientException;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderFailureType;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteRateLimitExceededException;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteRequestRejectedException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RouteApiErrorDecoderSupportTest {

    @Test
    @DisplayName("400 응답은 Fallback과 재시도를 하지 않는 요청 오류로 분류한다.")
    void decode_badRequest_returnsRequestRejected() {
        RuntimeException result = RouteApiErrorDecoderSupport.decode(
                MapProvider.KAKAO,
                response(400, Map.of())
        );

        assertThat(result).isInstanceOf(RouteRequestRejectedException.class);
    }

    @Test
    @DisplayName("401 응답은 다른 공급자로 Fallback 가능한 공급자 설정 오류로 분류한다.")
    void decode_unauthorized_returnsProviderConfigurationError() {
        RuntimeException result = RouteApiErrorDecoderSupport.decode(
                MapProvider.KAKAO,
                response(401, Map.of())
        );

        assertThat(result).isInstanceOf(RouteProviderConfigurationException.class);
    }

    @Test
    @DisplayName("429 응답은 Retry-After를 포함한 Rate Limit 오류로 분류한다.")
    void decode_tooManyRequests_preservesRetryAfter() {
        RuntimeException result = RouteApiErrorDecoderSupport.decode(
                MapProvider.NAVER,
                response(429, Map.of("Retry-After", List.of("7")))
        );

        assertThat(result).isInstanceOf(RouteRateLimitExceededException.class);
        assertThat(((RouteRateLimitExceededException) result).retryAfter())
                .isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    @DisplayName("5xx 응답은 CircuitBreaker에 기록할 일시 장애로 분류한다.")
    void decode_serverError_returnsTransientError() {
        RuntimeException result = RouteApiErrorDecoderSupport.decode(
                MapProvider.NAVER,
                response(503, Map.of())
        );

        assertThat(result).isInstanceOf(RouteProviderTransientException.class);
        assertThat(((RouteProviderTransientException) result).failureType())
                .isEqualTo(RouteProviderFailureType.SERVER_ERROR);
    }

    private Response response(int status, Map<String, java.util.Collection<String>> headers) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "https://route.test",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        return Response.builder()
                .status(status)
                .reason("test")
                .request(request)
                .headers(headers)
                .build();
    }
}
