package com.jumunhasyeo.hub.presentation;

import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.application.dto.response.HubRes;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.presentation.dto.request.CreateHubReq;
import com.jumunhasyeo.hub.hub.presentation.dto.request.DeleteHubReq;
import com.jumunhasyeo.hub.hub.presentation.dto.request.UpdateHubReq;
import com.jumunhasyeo.testsupport.ControllerSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HubWebControllerTest extends ControllerSliceTest {
    @Test
    @DisplayName("허브 생성 API로 허브 생성을 요청할 수 있다.")
    void create_hub_success() throws Exception {
        // given

        CreateHubReq request = new CreateHubReq(null, "이름", "서울시 송파구 허브", 12.6, 12.6, HubType.CENTER);
        UUID hubId = UUID.randomUUID();
        HubRes response = HubRes.builder()
                .id(hubId)
                .name("이름")
                .address("서울시 송파구 허브")
                .latitude(12.6)
                .longitude(12.6)
                .build();

        given(hubService.create(any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(hubId.toString()))
                .andExpect(jsonPath("$.data.name").value("이름"))
                .andExpect(jsonPath("$.data.address").value("서울시 송파구 허브"))
                .andExpect(jsonPath("$.data.latitude").value(12.6))
                .andExpect(jsonPath("$.data.longitude").value(12.6))
                .andReturn();
    }

    @Test
    @DisplayName("허브 생성 API로 허브 생성시 중복된 이름은 예외 반환")
    void create_duplicateName_shouldThrowException() throws Exception {
        // given
        CreateHubReq request = new CreateHubReq(null, "이름", "서울시 송파구 허브", 12.6, 12.6, HubType.CENTER);
        UUID hubId = UUID.randomUUID();
        HubRes response = HubRes.builder()
                .id(hubId)
                .name("이름")
                .address("서울시 송파구 허브")
                .latitude(12.6)
                .longitude(12.6)
                .build();

        given(hubService.create(any()))
                .willThrow(new DataIntegrityViolationException("p_hub_name_key"));

        // when & then
        mockMvc.perform(post("/api/v1/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ALREADY_EXISTS.name()))
                .andExpect(jsonPath("$.message").value(containsString("이미 존재하는 허브 이름입니다.")));
    }

    @Test
    @DisplayName("허브 생성 API를 요청할 때 name == null일 경우 예외반환")
    void create_NameIsNull_ShouldThrowException() throws Exception {
        // given
        CreateHubReq request = new CreateHubReq(null, null, "서울시 송파구 허브", 12.6, 12.6, HubType.CENTER);
        assertValidationFailed(request,"name=허브 이름은 필수입니다");
    }

    @Test
    @DisplayName("허브 생성 API를 요청할 때 address == null일 경우 예외반환")
    void create_AddressIsNull_ShouldThrowException() throws Exception {
        // given
        CreateHubReq request = new CreateHubReq(null, "이름", null, 12.6, 12.6, HubType.CENTER);
        assertValidationFailed(request, "");
    }

    @Test
    @DisplayName("허브 생성 API를 요청할 때 Longitude == null일 경우 예외반환")
    void create_LongitudeIsNull_ShouldThrowException() throws Exception {
        // given
        CreateHubReq request = new CreateHubReq(null, "이름", "서울시 송파구 허브", 12.6, null, HubType.CENTER);
        assertValidationFailed(request, "");
    }

    @Test
    @DisplayName("허브 생성 API를 요청할 때 Latitude == null일 경우 예외반환")
    void create_LatitudeIsNull_ShouldThrowException() throws Exception {
        // given
        CreateHubReq request = new CreateHubReq(null, "이름", "서울시 송파구 허브", null, 12.6, HubType.CENTER);
        assertValidationFailed(request, "");
    }

    @Test
    @DisplayName("허브 수정 API로 허브 수정을 요청할 수 있다.")
    void update_hub_success() throws Exception {
        // given
        UpdateHubReq request = new UpdateHubReq(UUID.randomUUID(), "이름", "서울시 송파구 허브", 12.6, 12.6);
        UUID hubId = UUID.randomUUID();
        HubRes response = HubRes.builder()
                .id(hubId)
                .name("이름")
                .address("서울시 송파구 허브")
                .latitude(12.6)
                .longitude(12.6)
                .build();

        given(hubService.update(any())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(hubId.toString()))
                .andExpect(jsonPath("$.data.name").value("이름"))
                .andExpect(jsonPath("$.data.address").value("서울시 송파구 허브"))
                .andExpect(jsonPath("$.data.latitude").value(12.6))
                .andExpect(jsonPath("$.data.longitude").value(12.6))
                .andReturn();
    }

    @Test
    @DisplayName("허브 삭제 성공")
    void delete_Hub_Success() throws Exception {
        //given
        UUID hubId = UUID.randomUUID();
        Long deleterId = 1L;
        DeleteHubReq request = new DeleteHubReq(hubId, deleterId);
        given(hubService.delete(any())).willReturn(hubId);

        // when & then
        mockMvc.perform(delete("/api/v1/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.id").value(hubId.toString()))
                .andReturn();
    }

    private void assertValidationFailed(CreateHubReq request, String validMessage) throws Exception {
        UUID hubId = UUID.randomUUID();
        HubRes response = HubRes.builder()
                .id(hubId)
                .name("이름")
                .address("서울시 송파구 허브")
                .latitude(12.6)
                .longitude(12.6)
                .build();

        given(hubService.create(any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value(containsString(validMessage)));
    }

    @Test
    @DisplayName("허브 name 검색 조회 성공")
    void search_HubName_Success() throws Exception {
        //given
        UUID hubId = UUID.randomUUID();
        HubRes response = HubRes.builder()
                .id(hubId)
                .name("송파허브")
                .address("서울시 송파구 허브")
                .latitude(12.6)
                .longitude(12.6)
                .build();

        Page<HubRes> page = new PageImpl(List.of(response), PageRequest.of(0, 10), 0);
        given(hubService.search(any(), any())).willReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/hubs/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("name","송파허브"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.content[0].id").value(hubId.toString()))
                .andReturn();
    }

    @Test
    @DisplayName("허브 삭제할 때 hubId == null일 경우 예외 반환")
    void delete_hubIdIsNull_ShouldThrowException() throws Exception {
        //given
        UUID hubId = null;
        Long deleterId = 1L;
        DeleteHubReq request = new DeleteHubReq(hubId, deleterId);
        assertValidationFailed(request, "hubId=허브 id는 필수입니다");
    }

    @Test
    @DisplayName("허브 삭제할 때 userId == null일 경우 예외 반환")
    void delete_userIdIsNull_ShouldThrowException() throws Exception {
        //given
        UUID hubId = UUID.randomUUID();
        Long deleterId = null;
        DeleteHubReq request = new DeleteHubReq(hubId, deleterId);
        assertValidationFailed(request, "userId=삭제 요청자 id는 필수입니다");
    }

    @Test
    @DisplayName("허브 수정 API를 요청할 때 Latitude == null일 경우 예외반환")
    void update_LatitudeIsNull_ShouldThrowException() throws Exception {
        // given
        UpdateHubReq request = new UpdateHubReq(UUID.randomUUID(), "이름", "서울시 송파구 허브", null, 12.6);
        assertValidationFailed(request, " ");
    }

    @Test
    @DisplayName("허브 수정 API를 요청할 때 longitude == null일 경우 예외반환")
    void update_LongitudeIsNull_ShouldThrowException() throws Exception {
        // given
        UpdateHubReq request = new UpdateHubReq(UUID.randomUUID(), "이름", "서울시 송파구 허브", 12.6, null);
        assertValidationFailed(request, "");
    }

    @Test
    @DisplayName("허브 수정 API를 요청할 때 name == null일 경우 예외반환")
    void update_NameIsNull_ShouldThrowException() throws Exception {
        // given
        UpdateHubReq request = new UpdateHubReq(UUID.randomUUID(), null, "서울 송파구 허브", 12.6, 12.6);
        assertValidationFailed(request, "");
    }

    @Test
    @DisplayName("허브 수정 API를 요청할 때 address == null일 경우 예외반환")
    void update_AddressIsNull_ShouldThrowException() throws Exception {
        // given
        UpdateHubReq request = new UpdateHubReq(UUID.randomUUID(), "이름", null, 12.6, 12.6);
        assertValidationFailed(request, " ");
    }

    private void assertValidationFailed(DeleteHubReq request, String substring) throws Exception {
        given(hubService.delete(any())).willReturn(request.hubId());

        // when & then
        mockMvc.perform(delete("/api/v1/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value(containsString(substring)));
    }

    private void assertValidationFailed(UpdateHubReq request, String validMessage) throws Exception {
        UUID hubId = UUID.randomUUID();
        HubRes response = HubRes.builder()
                .id(hubId)
                .name("이름")
                .address("서울시 송파구 허브")
                .latitude(12.6)
                .longitude(12.6)
                .build();

        given(hubService.create(any())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value(containsString(validMessage)));
    }
}
