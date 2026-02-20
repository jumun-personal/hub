package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;

public class RouteProviderTransientException extends RuntimeException {

    private final MapProvider provider;
    private final RouteProviderFailureType failureType;

    public RouteProviderTransientException(MapProvider provider, String message) {
        this(provider, RouteProviderFailureType.UNKNOWN, message, null);
    }

    public RouteProviderTransientException(MapProvider provider, String message, Throwable cause) {
        this(provider, RouteProviderFailureType.UNKNOWN, message, cause);
    }

    public RouteProviderTransientException(
            MapProvider provider,
            RouteProviderFailureType failureType,
            String message
    ) {
        this(provider, failureType, message, null);
    }

    public RouteProviderTransientException(
            MapProvider provider,
            RouteProviderFailureType failureType,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.provider = provider;
        this.failureType = failureType;
    }

    public MapProvider provider() {
        return provider;
    }

    public RouteProviderFailureType failureType() {
        return failureType;
    }
}
