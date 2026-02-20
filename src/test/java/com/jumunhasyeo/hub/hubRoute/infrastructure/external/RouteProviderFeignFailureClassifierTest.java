package com.jumunhasyeo.hub.hubRoute.infrastructure.external;

import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderFailureType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class RouteProviderFeignFailureClassifierTest {

    @Test
    @DisplayName("Socket Timeout은 외부 공급자 Timeout으로 분류한다")
    void classify_whenSocketTimeout_returnsTimeout() {
        // given
        RuntimeException exception = new RuntimeException(new SocketTimeoutException("read timed out"));

        // when
        RouteProviderFailureType result = RouteProviderFeignFailureClassifier.classify(exception);

        // then
        assertThat(result).isEqualTo(RouteProviderFailureType.TIMEOUT);
    }

    @Test
    @DisplayName("연결 거부는 외부 공급자 연결 실패로 분류한다")
    void classify_whenConnectionFails_returnsConnectionFailure() {
        // given
        RuntimeException exception = new RuntimeException(new ConnectException("connection refused"));

        // when
        RouteProviderFailureType result = RouteProviderFeignFailureClassifier.classify(exception);

        // then
        assertThat(result).isEqualTo(RouteProviderFailureType.CONNECTION);
    }
}
