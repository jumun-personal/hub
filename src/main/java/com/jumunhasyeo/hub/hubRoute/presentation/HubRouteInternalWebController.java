package com.jumunhasyeo.hub.hubRoute.presentation;

import com.jumunhasyeo.common.ApiRes;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.HubRouteBuildJobRes;
import com.jumunhasyeo.hub.hubRoute.application.dto.response.HubRouteRes;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteBuildJobService;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRoutePlanningService;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.presentation.docs.ApiDocGetAllHubRoutes;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Internal-HubRoute", description = "내부용 허브 경로 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/api/v1/hubs")
public class HubRouteInternalWebController {

    private final HubRouteService hubRouteService;
    private final HubRouteBuildJobService hubRouteBuildJobService;
    private final HubRoutePlanningService hubRoutePlanningService;

    //허브 경로 전체 조회
    @ApiDocGetAllHubRoutes
    @GetMapping("/routes")
    public ResponseEntity<ApiRes<List<HubRouteRes>>> getAll() {
        List<HubRouteRes> hubRouteRes = hubRouteService.getALLRoute();
        return ResponseEntity.ok(ApiRes.success(hubRouteRes));
    }

    @GetMapping("/{hubId}/route-build")
    public ResponseEntity<ApiRes<HubRouteBuildJobRes>> getRouteBuildJob(
            @PathVariable(name = "hubId") UUID hubId
    ) {
        return ResponseEntity.ok(ApiRes.success(hubRouteBuildJobService.get(hubId)));
    }

    @PostMapping("/{hubId}/route-build/retry")
    public ResponseEntity<ApiRes<HubRouteBuildJobRes>> retryRouteBuildJob(
            @PathVariable(name = "hubId") UUID hubId
    ) {
        hubRoutePlanningService.retryFailed(hubId);
        return ResponseEntity.ok(ApiRes.success(hubRouteBuildJobService.get(hubId)));
    }
}
