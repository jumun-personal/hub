package com.jumunhasyeo.stock.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockHistoryTest {

    @Test
    @DisplayName("ofStore는 STORE 타입 이력을 생성한다.")
    void ofStore_success() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        StockHistory history = StockHistory.ofStore(hubId, productId, 15, "idem-store");

        assertThat(history.getHubId()).isEqualTo(hubId);
        assertThat(history.getProductId()).isEqualTo(productId);
        assertThat(history.getType()).isEqualTo(StockHistory.StockHistoryType.STORE);
        assertThat(history.getQuantity()).isEqualTo(15);
    }

    @Test
    @DisplayName("ofShipped는 SHIPPED 타입 이력을 생성한다.")
    void ofShipped_success() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        StockHistory history = StockHistory.ofShipped(hubId, productId, 3, "idem-shipped");

        assertThat(history.getHubId()).isEqualTo(hubId);
        assertThat(history.getProductId()).isEqualTo(productId);
        assertThat(history.getType()).isEqualTo(StockHistory.StockHistoryType.SHIPPED);
        assertThat(history.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("StockHistoryType 설명을 조회할 수 있다.")
    void stockHistoryType_description() {
        assertThat(StockHistory.StockHistoryType.STORE.getDescription()).isEqualTo("입고");
        assertThat(StockHistory.StockHistoryType.SHIPPED.getDescription()).isEqualTo("출고");
    }
}
