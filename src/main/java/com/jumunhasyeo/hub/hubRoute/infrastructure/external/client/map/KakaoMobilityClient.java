package com.jumunhasyeo.hub.hubRoute.infrastructure.external.client.map;

import com.jumunhasyeo.hub.hubRoute.infrastructure.response.KakaoRouteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "kakao-mobility-client",
        url = "${kakao.mobility.base-url:https://apis-navi.kakaomobility.com}",
        configuration = MapApiFeignClientConfig.class
)
public interface KakaoMobilityClient {

    @GetMapping("/v1/directions")
    KakaoRouteResponse getDirections(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("origin") String origin,
            @RequestParam("destination") String destination,
            @RequestParam(value = "waypoints", required = false) String waypoints,
            @RequestParam(value = "priority", defaultValue = "RECOMMEND") String priority,
            @RequestParam(value = "car_fuel", defaultValue = "GASOLINE") String carFuel,
            @RequestParam(value = "car_hipass", defaultValue = "false") Boolean carHipass,
            @RequestParam(value = "alternatives", defaultValue = "false") Boolean alternatives,
            @RequestParam(value = "road_details", defaultValue = "false") Boolean roadDetails
    );
}
