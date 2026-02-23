package com.jumunhasyeo.stock.application.dto.response;

import com.jumunhasyeo.stock.domain.entity.StockHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "재고 이력 응답")
public record StockHistoryRes(
        @Schema(description = "이력 ID")
        UUID id,

        @Schema(description = "허브 ID")
        UUID hubId,
        
        @Schema(description = "상품 ID")
        UUID productId,
        
        @Schema(description = "이력 타입 (DECREASE: 재고 감소, INCREASE: 재고 증가)")
        String type,
        
        @Schema(description = "수량")
        int quantity,
        
        @Schema(description = "메모")
        String memo,
        
        @Schema(description = "생성일시")
        LocalDateTime createdAt
) {
    public static StockHistoryRes from(StockHistory stockHistory) {
        return new StockHistoryRes(
                stockHistory.getId(),
                stockHistory.getHubId(),
                stockHistory.getProductId(),
                stockHistory.getType().name(),
                stockHistory.getQuantity(),
                stockHistory.getMemo(),
                stockHistory.getCreatedAt()
        );
    }
}
