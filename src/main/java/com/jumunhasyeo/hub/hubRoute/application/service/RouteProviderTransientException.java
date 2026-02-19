package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;

public class RouteProviderTransientException extends RuntimeException {

    private final MapProvider provider;

    public RouteProviderTransientException(MapProvider provider, String message) {
        super(message);
        this.provider = provider;
    }

    public RouteProviderTransientException(MapProvider provider, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
    }

    public MapProvider provider() {
        return provider;
    }
}
