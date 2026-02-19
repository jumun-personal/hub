package com.jumunhasyeo.hub.hubRoute.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RouteDelayPolicy {

    @Value("${hub.route.build.retry-backoff:10s}")
    private String initialRetryBackoff;

    @Value("${hub.route.build.primary-retry-delay:5s}")
    private String primaryRetryDelay;

    @Value("${hub.route.build.retry-backoff-max:2m}")
    private String maxRetryBackoff;

    @Value("${hub.route.build.retry-jitter-max:2s}")
    private String retryJitterMax;

    @Value("${hub.route.rate-limit.retry-delay:1s}")
    private String defaultRateLimitDelay;

    @Value("${hub.route.rate-limit.retry-jitter-max:500ms}")
    private String rateLimitJitterMax;

    public Duration buildRetryDelay(int completedFailures) {
        Duration initial = parse(initialRetryBackoff, "10s");
        Duration maximum = parse(maxRetryBackoff, "2m");
        int exponent = Math.max(0, Math.min(completedFailures, 30));
        long multiplier = 1L << exponent;
        Duration backoff;
        try {
            backoff = initial.multipliedBy(multiplier);
        } catch (ArithmeticException e) {
            backoff = maximum;
        }
        return min(backoff, maximum).plus(randomJitter(parse(retryJitterMax, "2s")));
    }

    public Duration primaryRetryDelay() {
        return parse(primaryRetryDelay, "5s")
                .plus(randomJitter(parse(retryJitterMax, "2s")));
    }

    public Duration rateLimitDelay(Duration retryAfter) {
        Duration base = retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()
                ? parse(defaultRateLimitDelay, "1s")
                : retryAfter;
        return base.plus(randomJitter(parse(rateLimitJitterMax, "500ms")));
    }

    private Duration randomJitter(Duration maximum) {
        long maxMillis = Math.max(0, maximum.toMillis());
        if (maxMillis == 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(maxMillis + 1));
    }

    private Duration parse(String value, String fallback) {
        return DurationStyle.detectAndParse(value == null || value.isBlank() ? fallback : value);
    }

    private Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }
}
