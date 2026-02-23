package com.jumunhasyeo.stock.application.dto.response;

import com.jumunhasyeo.stock.domain.entity.StockHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "재고 변경 응답")
public record StockChangeRes(
        @Schema(description = "허브 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID hubId,
        @Schema(description = "상품 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID productId,
        @Schema(description = "재고 변경 유형", example = "DECREASE")
        StockHistory.StockHistoryType type,
        @Schema(description = "변경 수량", example = "3")
        int quantity
) {
    public static StockChangeRes decrease(UUID hubId, UUID productId, int quantity) {
        return new StockChangeRes(hubId, productId, StockHistory.StockHistoryType.DECREASE, quantity);
    }

    public static StockChangeRes increase(UUID hubId, UUID productId, int quantity) {
        return new StockChangeRes(hubId, productId, StockHistory.StockHistoryType.INCREASE, quantity);
    }
}
