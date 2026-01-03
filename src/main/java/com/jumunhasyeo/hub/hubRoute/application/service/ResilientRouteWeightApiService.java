package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.dto.request.RouteWeightQuery;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.infrastructure.external.KakaoWeightRouteApiServiceImpl;
import com.jumunhasyeo.hub.hubRoute.infrastructure.external.NaverWeightRouteApiServiceImpl;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@RequiredArgsConstructor
public class ResilientRouteWeightApiService implements RouteWeightApiService {

    private final KakaoWeightRouteApiServiceImpl kakaoStrategy;
    private final NaverWeightRouteApiServiceImpl naverStrategy;

    @Override
    @Retry(name = "kakaoRoute")
    @CircuitBreaker(name = "kakaoRoute", fallbackMethod = "fallbackToNaver")
    public RouteWeightResult getRouteInfo(RouteWeightQuery query) {
        return kakaoStrategy.getWeight(query);
    }

    private RouteWeightResult fallbackToNaver(RouteWeightQuery query, Throwable throwable) {
        RouteWeightResult result = naverStrategy.getWeight(query);
        return new RouteWeightResult(
                result.distanceKm(),
                result.durationMinutes(),
                result.provider(),
                true
        );
    }
}
