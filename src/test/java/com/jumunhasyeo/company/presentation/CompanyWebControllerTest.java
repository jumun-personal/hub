package com.jumunhasyeo.company.presentation;

import com.jumunhasyeo.company.application.dto.response.CompanyRes;
import com.jumunhasyeo.company.domain.entity.CompanyType;
import com.jumunhasyeo.company.presentation.dto.request.CreateCompanyReq;
import com.jumunhasyeo.company.presentation.dto.request.DeleteCompanyReq;
import com.jumunhasyeo.company.presentation.dto.request.UpdateCompanyReq;
import com.jumunhasyeo.testsupport.ControllerSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyWebControllerTest extends ControllerSliceTest {
    @Test
    @DisplayName("업체를 생성할 수 있다.")
    void create_success() throws Exception {
        //given
        UUID hubId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        CreateCompanyReq req = new CreateCompanyReq(hubId, "테스트업체", CompanyType.PRODUCER, "서울시");
        CompanyRes res = new CompanyRes(companyId, hubId, "테스트업체", CompanyType.PRODUCER, "서울시");
        given(companyService.create(any())).willReturn(res);

        //when & then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.data.name").value("테스트업체"));
    }

    @Test
    @DisplayName("업체를 단건 조회할 수 있다.")
    void getById_success() throws Exception {
        //given
        UUID companyId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        CompanyRes res = new CompanyRes(companyId, hubId, "테스트업체", CompanyType.PRODUCER, "서울시");
        given(companyService.getById(companyId)).willReturn(res);

        //when & then
        mockMvc.perform(get("/api/v1/companies/{companyId}", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.data.name").value("테스트업체"));
    }

    @Test
    @DisplayName("업체 전체를 조회할 수 있다.")
    void getAll_success() throws Exception {
        //given
        List<CompanyRes> resList = List.of(
                new CompanyRes(UUID.randomUUID(), UUID.randomUUID(), "업체1", CompanyType.PRODUCER, "서울"),
                new CompanyRes(UUID.randomUUID(), UUID.randomUUID(), "업체2", CompanyType.RECEIVER, "부산")
        );
        given(companyService.getAll()).willReturn(resList);

        //when & then
        mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("업체를 수정할 수 있다.")
    void update_success() throws Exception {
        //given
        UUID companyId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        UpdateCompanyReq req = new UpdateCompanyReq(companyId, hubId, "수정업체", CompanyType.RECEIVER, "부산시");
        CompanyRes res = new CompanyRes(companyId, hubId, "수정업체", CompanyType.RECEIVER, "부산시");
        given(companyService.update(any())).willReturn(res);

        //when & then
        mockMvc.perform(patch("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수정업체"));
    }

    @Test
    @DisplayName("업체를 삭제할 수 있다.")
    void delete_success() throws Exception {
        //given
        UUID companyId = UUID.randomUUID();
        DeleteCompanyReq req = new DeleteCompanyReq(companyId, 1L);
        given(companyService.delete(any())).willReturn(companyId);

        //when & then
        mockMvc.perform(delete("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(companyId.toString()));
    }

}
