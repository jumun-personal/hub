package com.jumunhasyeo.hub.hubRoute.infrastructure.external;

import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderFailureType;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;

final class RouteProviderFeignFailureClassifier {

    private RouteProviderFeignFailureClassifier() {
    }

    static RouteProviderFailureType classify(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return RouteProviderFailureType.TIMEOUT;
            }
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof NoRouteToHostException) {
                return RouteProviderFailureType.CONNECTION;
            }
            current = current.getCause();
        }
        return RouteProviderFailureType.UNKNOWN;
    }
}
