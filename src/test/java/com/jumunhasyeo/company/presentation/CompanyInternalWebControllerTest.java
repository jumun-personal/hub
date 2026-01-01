package com.jumunhasyeo.company.presentation;

import com.jumunhasyeo.testsupport.AbstractControllerWebMvcTest;
import com.jumunhasyeo.testsupport.UnifiedControllerSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UnifiedControllerSliceTest
class CompanyInternalWebControllerTest extends AbstractControllerWebMvcTest {
    @Test
    @DisplayName("업체 존재 여부를 검증할 수 있다 - 존재하는 경우")
    void exists_true() throws Exception {
        //given
        UUID companyId = UUID.randomUUID();
        given(companyService.existsById(companyId)).willReturn(true);

        //when & then
        mockMvc.perform(get("/internal/api/v1/companies/{companyId}/exists", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("업체 존재 여부를 검증할 수 있다 - 존재하지 않는 경우")
    void exists_false() throws Exception {
        //given
        UUID companyId = UUID.randomUUID();
        given(companyService.existsById(companyId)).willReturn(false);

        //when & then
        mockMvc.perform(get("/internal/api/v1/companies/{companyId}/exists", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("업체 소속 허브를 검증할 수 있다 - 소속인 경우")
    void existToHub_true() throws Exception {
        //given
        UUID companyId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        given(companyService.existsByIdAndHubId(companyId, hubId)).willReturn(true);

        //when & then
        mockMvc.perform(get("/internal/api/v1/companies/{companyId}/hub/{hubId}/exists", companyId, hubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("업체 소속 허브를 검증할 수 있다 - 소속이 아닌 경우")
    void existToHub_false() throws Exception {
        //given
        UUID companyId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        given(companyService.existsByIdAndHubId(companyId, hubId)).willReturn(false);

        //when & then
        mockMvc.perform(get("/internal/api/v1/companies/{companyId}/hub/{hubId}/exists", companyId, hubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }
}
