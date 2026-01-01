package com.jumunhasyeo.product.presentation.controller;

import com.jumunhasyeo.product.application.dto.ProductRes;
import com.jumunhasyeo.product.domain.entity.Product;
import com.jumunhasyeo.product.presentation.ProductController;
import com.jumunhasyeo.product.presentation.dto.req.CreateProductReq;
import com.jumunhasyeo.product.presentation.dto.req.UpdateProductReq;
import com.jumunhasyeo.testsupport.AbstractControllerWebMvcTest;
import com.jumunhasyeo.testsupport.UnifiedControllerSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static com.jumunhasyeo.product.fixtures.ProductFixture.getProduct;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UnifiedControllerSliceTest
public class ProductControllerTest extends AbstractControllerWebMvcTest {

    @Test
    @DisplayName("상품 생성 API")
    void createProduct() throws Exception {
        // given
        CreateProductReq req = new CreateProductReq("상품", 10000, "상품 설명");
        ProductRes response = ProductRes.of(getProduct());

        given(productService.createProduct(any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(req.name()));
    }

    @Test
    @DisplayName("상품 수정 API")
    void updateProduct() throws Exception {
        // given
        UpdateProductReq req = new UpdateProductReq("상품", 10000, "상품 설명");
        ProductRes response = ProductRes.of(getProduct());

        given(productService.updateProduct(any())).willReturn(response);

        // when & then
        mockMvc.perform(put("/api/v1/products/{productId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(req.name()));
    }

    @Test
    @DisplayName("상품 삭제 API")
    void deleteProduct() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/products/{productId}", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("상품 단건 조회 API")
    void getProducts() throws Exception {
        // given
        Product product = getProduct();
        ProductRes response = ProductRes.of(product);

        given(productService.getProduct(any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/products/{productId}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(product.getName().getName()));
    }

    @Test
    @DisplayName("상품 다건 조회 API")
    void searchProducts() throws Exception {
        // given
        ProductRes productRes1 = ProductRes.of(getProduct());
        ProductRes productRes2 = ProductRes.of(getProduct());
        Page<ProductRes> response = new PageImpl<>(List.of(productRes1, productRes2), PageRequest.of(0, 10), 2);

        given(productService.searchProduct(any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/products", UUID.randomUUID())
                        .param("companyId", String.valueOf(UUID.randomUUID()))
                        .param("name", "상품")
                        .param("maxPrice", "100000")
                        .param("minPrice", (String) null)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }
}
