package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteProviderAvailabilityService {

    private static final String PROVIDERS_UNAVAILABLE_KEY = "hub-route:providers:unavailable";

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${hub.route.provider-unavailable-ttl:30s}")
    private String providerUnavailableTtl;

    public void markAllProvidersUnavailable(String reason) {
        try {
            Duration ttl = providerUnavailableTtl();
            stringRedisTemplate.opsForValue().set(
                    PROVIDERS_UNAVAILABLE_KEY,
                    reason == null ? "unknown" : reason,
                    ttl
            );
            log.warn("Route providers unavailable. hub creation blocked for ttl={}", ttl);
        } catch (RuntimeException e) {
            log.warn("Failed to mark route providers unavailable in Redis", e);
        }
    }

    public void clearAllProvidersUnavailable() {
        try {
            stringRedisTemplate.delete(PROVIDERS_UNAVAILABLE_KEY);
        } catch (RuntimeException e) {
            log.warn("Failed to clear route provider availability in Redis", e);
        }
    }

    public void assertRouteCreationAvailable() {
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(PROVIDERS_UNAVAILABLE_KEY))) {
                throw new BusinessException(ErrorCode.HUB_ROUTE_PROVIDER_UNAVAILABLE);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Failed to read route provider availability from Redis. fail-open.", e);
        }
    }

    private Duration providerUnavailableTtl() {
        return DurationStyle.detectAndParse(providerUnavailableTtl);
    }
}
