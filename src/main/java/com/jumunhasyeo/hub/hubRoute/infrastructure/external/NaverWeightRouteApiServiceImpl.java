package com.jumunhasyeo.hub.hubRoute.infrastructure.external;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import com.jumunhasyeo.hub.hubRoute.application.dto.request.RouteWeightQuery;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderConfigurationException;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderTransientException;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteRequestRejectedException;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteWeightStrategy;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteRateLimitExceededException;
import com.jumunhasyeo.hub.hubRoute.infrastructure.external.client.map.NaverMapClient;
import com.jumunhasyeo.hub.hubRoute.infrastructure.response.NaverRouteResponse;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaverWeightRouteApiServiceImpl implements RouteWeightStrategy {

    private final NaverMapClient naverMapClient;

    @Value("${naver.maps.api-key-id}")
    private String apiKeyId;

    @Value("${naver.maps.api-key}")
    private String apiKey;

    @Override
    public RouteWeightResult getWeight(RouteWeightQuery query) {
        try {
            Coordinate start = query.start();
            Coordinate end = query.end();

            String startParam = String.format("%f,%f", start.getLongitude(), start.getLatitude());
            String goalParam = String.format("%f,%f", end.getLongitude(), end.getLatitude());

            log.info("Requesting route from Naver API: {} -> {}", startParam, goalParam);

            NaverRouteResponse response = naverMapClient.getDirections(
                    apiKeyId,
                    apiKey,
                    startParam,
                    goalParam,
                    "trafast"
            );

            BigDecimal distanceKm = response.getDistanceKm();
            Integer durationMinutes = response.getDurationMinutes();
            return new RouteWeightResult(distanceKm, durationMinutes, MapProvider.NAVER, false);

        } catch (RouteRateLimitExceededException
                 | RouteRequestRejectedException
                 | RouteProviderConfigurationException
                 | RouteProviderTransientException e) {
            throw e;
        } catch (RetryableException e) {
            throw new RouteProviderTransientException(
                    MapProvider.NAVER,
                    RouteProviderFeignFailureClassifier.classify(e),
                    "Naver route API connection failed",
                    e
            );
        } catch (Exception e) {
            log.error("Failed to get route from Naver API, {}", e.toString());
            throw new RouteProviderTransientException(MapProvider.NAVER, "Naver route response handling failed", e);
        }
    }

    @Override
    public MapProvider provider() {
        return MapProvider.NAVER;
    }
}
