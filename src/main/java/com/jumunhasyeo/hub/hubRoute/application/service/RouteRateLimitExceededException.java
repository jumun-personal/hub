package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;

import java.time.Duration;

public class RouteRateLimitExceededException extends RuntimeException {

    private final MapProvider provider;
    private final Duration retryAfter;

    public RouteRateLimitExceededException(MapProvider provider) {
        this(provider, Duration.ofSeconds(1));
    }

    public RouteRateLimitExceededException(MapProvider provider, Duration retryAfter) {
        super(provider + " route rate limit exhausted");
        this.provider = provider;
        this.retryAfter = retryAfter;
    }

    public MapProvider provider() {
        return provider;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
