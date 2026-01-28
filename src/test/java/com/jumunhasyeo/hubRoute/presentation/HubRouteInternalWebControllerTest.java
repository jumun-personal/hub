package com.jumunhasyeo.hubRoute.presentation;

import com.jumunhasyeo.hub.hubRoute.application.dto.response.HubRouteRes;
import com.jumunhasyeo.testsupport.ControllerSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HubRouteInternalWebControllerTest extends ControllerSliceTest {
    @Test
    @DisplayName("허브 경로 전체 조회 API로 모든 허브 경로를 조회할 수 있다.")
    void get_all_routes_success() throws Exception {
        // given
        UUID routeId1 = UUID.randomUUID();
        UUID startHub1 = UUID.randomUUID();
        UUID endHub1 = UUID.randomUUID();

        UUID routeId2 = UUID.randomUUID();
        UUID startHub2 = UUID.randomUUID();
        UUID endHub2 = UUID.randomUUID();

        List<HubRouteRes> hubRouteResList = List.of(
                new HubRouteRes(routeId1, startHub1, endHub1, 30, 25),
                new HubRouteRes(routeId2, startHub2, endHub2, 45, 40)
        );

        given(hubRouteService.getALLRoute()).willReturn(hubRouteResList);

        // when & then
        mockMvc.perform(get("/internal/api/v1/hubs/routes")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].routeId").value(routeId1.toString()))
                .andExpect(jsonPath("$.data[0].startHub").value(startHub1.toString()))
                .andExpect(jsonPath("$.data[0].endHub").value(endHub1.toString()))
                .andExpect(jsonPath("$.data[0].durationMinutes").value(30))
                .andExpect(jsonPath("$.data[0].distanceKm").value(25))
                .andExpect(jsonPath("$.data[1].routeId").value(routeId2.toString()))
                .andExpect(jsonPath("$.data[1].startHub").value(startHub2.toString()))
                .andExpect(jsonPath("$.data[1].endHub").value(endHub2.toString()))
                .andExpect(jsonPath("$.data[1].durationMinutes").value(45))
                .andExpect(jsonPath("$.data[1].distanceKm").value(40))
                .andReturn();
    }

    @Test
    @DisplayName("허브 경로가 없을 때 빈 리스트를 반환한다.")
    void get_all_routes_returns_empty_list() throws Exception {
        // given
        given(hubRouteService.getALLRoute()).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/internal/api/v1/hubs/routes")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andReturn();
    }
}
