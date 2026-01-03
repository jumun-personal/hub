package com.jumunhasyeo.hub.hubRoute.infrastructure.external.client.map;

import com.jumunhasyeo.hub.hubRoute.infrastructure.response.NaverRouteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "naver-map-client",
        url = "${naver.maps.base-url}",
        configuration = NaverMapFeignClientConfig.class
)
public interface NaverMapClient {

    @GetMapping("/map-direction/v1/driving")
    NaverRouteResponse getDirections(
            @RequestHeader("X-NCP-APIGW-API-KEY-ID") String apiKeyId,
            @RequestHeader("X-NCP-APIGW-API-KEY") String apiKey,
            @RequestParam("start") String start,
            @RequestParam("goal") String goal,
            @RequestParam(value = "option", defaultValue = "trafast") String option
    );
}
