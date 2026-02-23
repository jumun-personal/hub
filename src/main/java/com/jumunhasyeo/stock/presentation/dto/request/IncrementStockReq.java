package com.jumunhasyeo.stock.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;
@Schema(description = "재고 증가 요청")
public record IncrementStockReq (
    @Schema(description = "허브 ID", example = "11111111-1111-1111-1111-111111111111", required = true)
    @NotNull(message = "허브 ID(hubId) 는 필수 입니다.")
    UUID hubId,

    @Schema(description = "상품 ID", example = "77777777-7777-7777-7777-777777777777", required = true)
    @NotNull(message = "상품 ID(productId) 는 필수 입니다.")
    UUID productId,

    @Schema(description = "증가량", example = "10", required = true)
    @NotNull(message = "증가량(quantity) 는 필수입니다")
    @Positive(message = "증가량(quantity)은 0보다 커야 합니다.")
    Integer quantity
){}
