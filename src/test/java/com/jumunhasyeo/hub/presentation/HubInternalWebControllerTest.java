package com.jumunhasyeo.hub.presentation;

import com.jumunhasyeo.hub.hub.application.dto.response.HubRes;
import com.jumunhasyeo.hub.hub.presentation.HubInternalWebController;
import com.jumunhasyeo.testsupport.AbstractControllerWebMvcTest;
import com.jumunhasyeo.testsupport.UnifiedControllerSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UnifiedControllerSliceTest
class HubInternalWebControllerTest extends AbstractControllerWebMvcTest {
    @Test
    @DisplayName("허브 존재 여부 확인 - 허브가 존재하는 경우")
    void exist_hub_when_hub_exists() throws Exception {
        // given
        UUID hubId = UUID.randomUUID();
        given(hubService.existById(hubId)).willReturn(true);

        // when & then
        mockMvc.perform(get("/internal/api/v1/hubs/{hubId}/exists", hubId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exist").value(true))
                .andReturn();
    }

    @Test
    @DisplayName("허브 존재 여부 확인 - 허브가 존재하지 않는 경우")
    void exist_hub_when_hub_not_exists() throws Exception {
        // given
        UUID hubId = UUID.randomUUID();
        given(hubService.existById(hubId)).willReturn(false);

        // when & then
        mockMvc.perform(get("/internal/api/v1/hubs/{hubId}/exists", hubId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exist").value(false))
                .andReturn();
    }

    @Test
    @DisplayName("허브 단건 조회 성공")
    void findById_Hub_Success() throws Exception {
        //given
        UUID hubId = UUID.randomUUID();
        HubRes response = HubRes.builder()
                .id(hubId)
                .name("송파허브")
                .address("서울시 송파구 허브")
                .latitude(12.6)
                .longitude(12.6)
                .build();

        given(hubService.getById(any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/internal/api/v1/hubs/{hubId}",hubId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(hubId.toString()))
                .andExpect(jsonPath("$.data.name").value("송파허브"))
                .andExpect(jsonPath("$.data.address").value("서울시 송파구 허브"))
                .andExpect(jsonPath("$.data.latitude").value(12.6))
                .andExpect(jsonPath("$.data.longitude").value(12.6))
                .andReturn();
    }
}
