package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.dto.request.RouteWeightQuery;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.RouteWeightResult;

/**
 * 경로 공급자 선택, 보호 정책과 fallback을 숨기는 resolution Module의 Interface.
 */
public interface RouteProviderResolution {

    RouteWeightResult resolve(RouteWeightQuery query);
}
