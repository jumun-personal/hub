package com.jumunhasyeo.stock.domain.entity;

import com.jumunhasyeo.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@Table(name = "p_stock_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "stock_history_id")
    private UUID id;

    @Column(name = "hub_id", nullable = false)
    private UUID hubId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private StockHistoryType type;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "idempotencyKey")
    private String idempotencyKey;

    private StockHistory(UUID hubId, UUID productId, StockHistoryType type, int quantity, String memo, String idempotencyKey) {
        this.hubId = hubId;
        this.productId = productId;
        this.type = type;
        this.quantity = quantity;
        this.idempotencyKey = idempotencyKey;
    }

    public static StockHistory ofStore(UUID hubId, UUID productId, int quantity, String idempotencyKey) {
        return new StockHistory(hubId, productId, StockHistoryType.STORE, quantity, null, idempotencyKey);
    }

    public static StockHistory ofShipped(UUID hubId, UUID productId, int quantity, String idempotencyKey) {
        return new StockHistory(hubId, productId, StockHistoryType.SHIPPED, quantity, null, idempotencyKey);
    }

    public enum StockHistoryType {
        STORE("입고"),
        SHIPPED("출고");

        private final String description;

        StockHistoryType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
