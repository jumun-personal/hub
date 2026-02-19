package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;

public class RouteProviderConfigurationException extends RuntimeException {

    private final MapProvider provider;

    public RouteProviderConfigurationException(MapProvider provider, String message) {
        super(message);
        this.provider = provider;
    }

    public MapProvider provider() {
        return provider;
    }
}
