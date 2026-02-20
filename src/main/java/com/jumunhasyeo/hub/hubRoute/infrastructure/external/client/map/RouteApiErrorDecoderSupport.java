package com.jumunhasyeo.hub.hubRoute.infrastructure.external.client.map;

import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderConfigurationException;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderTransientException;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderFailureType;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteRateLimitExceededException;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteRequestRejectedException;
import feign.Response;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;

final class RouteApiErrorDecoderSupport {

    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(1);

    private RouteApiErrorDecoderSupport() {
    }

    static RuntimeException decode(MapProvider provider, Response response) {
        int status = response.status();
        if (status == 400 || status == 404 || status == 422) {
            return new RouteRequestRejectedException(provider, message(provider, status, "request rejected"));
        }
        if (status == 401 || status == 403) {
            return new RouteProviderConfigurationException(provider, message(provider, status, "authentication failed"));
        }
        if (status == 429) {
            return new RouteRateLimitExceededException(provider, retryAfter(response));
        }
        if (status >= 500) {
            return new RouteProviderTransientException(
                    provider,
                    RouteProviderFailureType.SERVER_ERROR,
                    message(provider, status, "server error")
            );
        }
        return new RouteRequestRejectedException(provider, message(provider, status, "non-retryable response"));
    }

    private static Duration retryAfter(Response response) {
        String rawValue = response.headers().entrySet().stream()
                .filter(entry -> "Retry-After".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .findFirst()
                .orElse(null);
        if (rawValue == null || rawValue.isBlank()) {
            return DEFAULT_RETRY_AFTER;
        }
        try {
            long seconds = Long.parseLong(rawValue.trim());
            return Duration.ofSeconds(Math.max(1, seconds));
        } catch (NumberFormatException ignored) {
            try {
                Duration duration = Duration.between(
                        ZonedDateTime.now(),
                        ZonedDateTime.parse(rawValue.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                );
                return duration.isNegative() || duration.isZero() ? DEFAULT_RETRY_AFTER : duration;
            } catch (RuntimeException invalidHeader) {
                return DEFAULT_RETRY_AFTER;
            }
        }
    }

    private static String message(MapProvider provider, int status, String reason) {
        return provider + " route API " + reason + ". status=" + status;
    }
}
