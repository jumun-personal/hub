package com.jumunhasyeo.stock.presentation;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.dto.response.StockChangeRes;
import com.jumunhasyeo.stock.presentation.dto.request.DecreaseStockReq;
import com.jumunhasyeo.stock.presentation.dto.request.IncrementStockReq;
import com.jumunhasyeo.testsupport.ControllerSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockInternalWebControllerTest extends ControllerSliceTest {
    @Test
    @DisplayName("재고 감소 API로 재고 감소를 요청할 수 있다.")
    void decrement_stock_success() throws Exception {
        // given
        UUID productId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        ArrayList<DecreaseStockReq> reqs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            reqs.add(new DecreaseStockReq(hubId, productId, 100));
        }
        StockChangeRes stockChangeRes = StockChangeRes.decrease(hubId, productId, 100);

        given(stockService.decrement(any(), any())).willReturn(List.of(stockChangeRes));

        // when & then
        mockMvc.perform(post("/internal/api/v1/stocks/decrement")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].hubId").value(hubId.toString()))
                .andExpect(jsonPath("$.data[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.data[0].type").value("DECREASE"))
                .andExpect(jsonPath("$.data[0].quantity").value(100))
                .andReturn();
    }

    @Test
    @DisplayName("재고 증가 API로 재고 증가를 요청할 수 있다.")
    void increment_stock_success() throws Exception {
        // given
        UUID productId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        ArrayList<IncrementStockReq> request = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            request.add(new IncrementStockReq(hubId, productId, 100));
        }
        StockChangeRes stockChangeRes = StockChangeRes.increase(hubId, productId, 100);

        given(stockService.increment(any(), any())).willReturn(List.of(stockChangeRes));

        // when & then
        mockMvc.perform(post("/internal/api/v1/stocks/increment")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].hubId").value(hubId.toString()))
                .andExpect(jsonPath("$.data[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.data[0].type").value("INCREASE"))
                .andExpect(jsonPath("$.data[0].quantity").value(100))
                .andReturn();
    }

    @Test
    @DisplayName("재고 증가 중복 성공 충돌은 기존처럼 409를 반환한다.")
    void increment_stock_whenSuccessConflict_returns409() throws Exception {
        ArrayList<IncrementStockReq> request = new ArrayList<>();
        request.add(new IncrementStockReq(UUID.randomUUID(), UUID.randomUUID(), 100));
        given(stockService.increment(any(), any()))
                .willThrow(new BusinessException(ErrorCode.SUCCESS_CONFLICT_EXCEPTION));

        mockMvc.perform(post("/internal/api/v1/stocks/increment")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS_CONFLICT_EXCEPTION.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SUCCESS_CONFLICT_EXCEPTION.getMessage()));
    }
}
