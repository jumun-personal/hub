package com.jumunhasyeo.hub.hubRoute.application.service;

public class RoutePrimaryRetryRequiredException extends RuntimeException {

    public RoutePrimaryRetryRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
