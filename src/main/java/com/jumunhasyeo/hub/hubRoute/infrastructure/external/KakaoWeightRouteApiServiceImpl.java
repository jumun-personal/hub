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
import com.jumunhasyeo.hub.hubRoute.infrastructure.external.client.map.KakaoMobilityClient;
import com.jumunhasyeo.hub.hubRoute.infrastructure.response.KakaoRouteResponse;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoWeightRouteApiServiceImpl implements RouteWeightStrategy {

    private final KakaoMobilityClient kakaoMobilityClient;

    @Value("${kakao.mobility.api-key}")
    private String apiKey;

    /**
     * 두 좌표 간의 실제 경로 정보를 조회
     */
    public RouteWeightResult getWeight(RouteWeightQuery query){
        try {
            // Kakao API 형식: "경도,위도" (longitude,latitude)
            Coordinate start = query.start();
            Coordinate end = query.end();
            String origin = String.format("%f,%f", start.getLongitude(), start.getLatitude());
            String destination = String.format("%f,%f", end.getLongitude(), end.getLatitude());

            log.info("Requesting route from Kakao API: {} -> {}", origin, destination);

            KakaoRouteResponse response = kakaoMobilityClient.getDirections(
                    apiKey,
                    origin,
                    destination,
                    null,  // waypoints 없음
                    "RECOMMEND",    // 추천 경로
                    "GASOLINE",     // 휘발유
                    false,          // 하이패스 없음
                    false,          // 대안 경로 없음
                    false           // 도로 상세 정보 없음
            );

            if (response.getRoutes() == null || response.getRoutes().isEmpty()) {
                throw new BusinessException(ErrorCode.MAP_API_EXCEPTION);
            }

            KakaoRouteResponse.Route route = response.getRoutes().get(0);
            if (route.getResultCode() != 0) {
                throw new BusinessException(ErrorCode.MAP_API_EXCEPTION);
            }

            BigDecimal distanceKm = response.getDistanceKm();
            Integer durationMinutes = response.getDurationMinutes();
            return new RouteWeightResult(distanceKm, durationMinutes, MapProvider.KAKAO, false);

        } catch (RouteRateLimitExceededException
                 | RouteRequestRejectedException
                 | RouteProviderConfigurationException
                 | RouteProviderTransientException e) {
            throw e;
        } catch (RetryableException e) {
            throw new RouteProviderTransientException(MapProvider.KAKAO, "Kakao route API connection failed", e);
        } catch (Exception e) {
            log.error("Failed to get route from Kakao API, {}", e.toString());
            throw new RouteProviderTransientException(MapProvider.KAKAO, "Kakao route response handling failed", e);
        }
    }

    @Override
    public MapProvider provider() {
        return MapProvider.KAKAO;
    }

}
