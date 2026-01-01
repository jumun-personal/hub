package com.jumunhasyeo.stock.presentaion;

import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import com.jumunhasyeo.stock.presentation.StockWebController;
import com.jumunhasyeo.stock.presentation.dto.request.*;
import com.jumunhasyeo.testsupport.AbstractControllerWebMvcTest;
import com.jumunhasyeo.testsupport.UnifiedControllerSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UnifiedControllerSliceTest
class StockWebControllerTest extends AbstractControllerWebMvcTest {
    @Test
    @DisplayName("재고 생성 API로 재고 생성을 요청할 수 있다.")
    void create_stock_success() throws Exception {
        // given
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        Integer quantity = 100;
        CreateStockReq request = new CreateStockReq(hubId, productId, quantity);

        StockRes stockRes = StockRes.builder()
                .stockId(stockId)
                .hubId(hubId)
                .productId(productId)
                .quantity(quantity)
                .deletedAt(null)
                .deletedBy(null)
                .build();

        given(stockService.create(any())).willReturn(stockRes);

        // when & then
        mockMvc.perform(post("/api/v1/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockId").value(stockId.toString()))
                .andExpect(jsonPath("$.data.hubId").value(hubId.toString()))
                .andExpect(jsonPath("$.data.productId").value(productId.toString()))
                .andExpect(jsonPath("$.data.quantity").value(quantity))
                .andExpect(jsonPath("$.data.deletedAt").isEmpty())
                .andExpect(jsonPath("$.data.deletedBy").isEmpty())
                .andReturn();
    }

    @Test
    @DisplayName("재고 생성 시 hubId가 null이면 예외가 발생한다.")
    void create_stock_fail_when_hubId_is_null() throws Exception {
        // given
        CreateStockReq request = new CreateStockReq(null, UUID.randomUUID(), 100);

        // when & then
        mockMvc.perform(post("/api/v1/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value(containsString("hubId는 필수 입니다.")))
                .andReturn();
    }

    @Test
    @DisplayName("재고 생성 시 productId가 null이면 예외가 발생한다.")
    void create_stock_fail_when_productId_is_null() throws Exception {
        // given
        CreateStockReq request = new CreateStockReq(UUID.randomUUID(), null, 100);

        // when & then
        mockMvc.perform(post("/api/v1/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value(containsString("productId는 필수 입니다.")))
                .andReturn();
    }

    @Test
    @DisplayName("재고 생성 시 quantity가 null이면 예외가 발생한다.")
    void create_stock_fail_when_quantity_is_null() throws Exception {
        // given
        CreateStockReq request = new CreateStockReq(UUID.randomUUID(), UUID.randomUUID(), null);

        // when & then
        mockMvc.perform(post("/api/v1/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value(containsString("재고 수량은 필수 입니다.")))
                .andReturn();
    }

    @Test
    @DisplayName("재고 ID로 재고를 조회할 수 있다.")
    void get_stock_success() throws Exception {
        // given
        UUID stockId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        StockRes stockRes = StockRes.builder()
                .stockId(stockId)
                .productId(productId)
                .hubId(hubId)
                .quantity(100)
                .deletedAt(null)
                .deletedBy(null)
                .build();

        given(stockService.get(stockId)).willReturn(stockRes);

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}", stockId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockId").value(stockId.toString()))
                .andExpect(jsonPath("$.data.productId").value(productId.toString()))
                .andExpect(jsonPath("$.data.hubId").value(hubId.toString()))
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.deletedAt").isEmpty())
                .andExpect(jsonPath("$.data.deletedBy").isEmpty())
                .andReturn();
    }

    @Test
    @DisplayName("재고 삭제 API로 재고를 논리적으로 삭제할 수 있다.")
    void delete_stock_success() throws Exception {
        // given
        UUID stockId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        Long userId = 123L;
        DeleteStockReq req = new DeleteStockReq(stockId, userId);

        StockRes stockRes = StockRes.builder()
                .stockId(stockId)
                .productId(productId)
                .hubId(hubId)
                .quantity(100)
                .deletedAt(LocalDateTime.now())
                .deletedBy(userId)
                .build();

        given(stockService.delete(any())).willReturn(stockRes);

        // when & then
        mockMvc.perform(delete("/api/v1/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockId").value(stockId.toString()))
                .andExpect(jsonPath("$.data.productId").value(productId.toString()))
                .andExpect(jsonPath("$.data.hubId").value(hubId.toString()))
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.deletedAt").exists())
                .andExpect(jsonPath("$.data.deletedBy").value(userId))
                .andReturn();
    }
}
