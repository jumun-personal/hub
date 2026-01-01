package com.jumunhasyeo.stock.presentation;

import com.jumunhasyeo.stock.application.dto.response.StockRes;
import com.jumunhasyeo.stock.presentation.dto.request.DecreaseStockReq;
import com.jumunhasyeo.stock.presentation.dto.request.DecreaseStockReqList;
import com.jumunhasyeo.stock.presentation.dto.request.IncrementStockReq;
import com.jumunhasyeo.stock.presentation.dto.request.IncrementStockReqList;
import com.jumunhasyeo.testsupport.AbstractControllerWebMvcTest;
import com.jumunhasyeo.testsupport.UnifiedControllerSliceTest;
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

@UnifiedControllerSliceTest
class StockInternalWebControllerTest extends AbstractControllerWebMvcTest {
    @Test
    @DisplayName("재고 감소 API로 재고 감소를 요청할 수 있다.")
    void decrement_stock_success() throws Exception {
        // given
        UUID productId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        ArrayList<DecreaseStockReq> reqs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            reqs.add(new DecreaseStockReq(stockId, 100));
        }
        StockRes stockRes = StockRes.builder()
                .stockId(stockId)
                .hubId(UUID.randomUUID())
                .productId(productId)
                .quantity(200)
                .build();

        given(stockService.decrement(any(), any())).willReturn(List.of(stockRes));

        // when & then
        mockMvc.perform(post("/internal/api/v1/stocks/decrement")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("true"))
                .andReturn();
    }

    @Test
    @DisplayName("재고 증가 API로 재고 증가를 요청할 수 있다.")
    void increment_stock_success() throws Exception {
        // given
        UUID productId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        ArrayList<IncrementStockReq> request = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            request.add(new IncrementStockReq(stockId, 100));
        }
        StockRes stockRes = StockRes.builder()
                .stockId(stockId)
                .hubId(UUID.randomUUID())
                .productId(productId)
                .quantity(200)
                .build();

        given(stockService.increment(any(), any())).willReturn(List.of(stockRes));

        // when & then
        mockMvc.perform(post("/internal/api/v1/stocks/increment")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("true"))
                .andReturn();
    }
}
