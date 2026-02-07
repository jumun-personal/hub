package com.jumunhasyeo.stock.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockHistoryTest {

    @Test
    @DisplayName("ofDecrease는 DECREASE 타입 이력을 생성한다.")
    void ofDecrease_success() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        StockHistory history = StockHistory.ofDecrease(hubId, productId, 15, "idem-decrease");

        assertThat(history.getHubId()).isEqualTo(hubId);
        assertThat(history.getProductId()).isEqualTo(productId);
        assertThat(history.getType()).isEqualTo(StockHistory.StockHistoryType.DECREASE);
        assertThat(history.getQuantity()).isEqualTo(15);
    }

    @Test
    @DisplayName("ofIncrease는 INCREASE 타입 이력을 생성한다.")
    void ofIncrease_success() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        StockHistory history = StockHistory.ofIncrease(hubId, productId, 3, "idem-increase");

        assertThat(history.getHubId()).isEqualTo(hubId);
        assertThat(history.getProductId()).isEqualTo(productId);
        assertThat(history.getType()).isEqualTo(StockHistory.StockHistoryType.INCREASE);
        assertThat(history.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("StockHistoryType 설명을 조회할 수 있다.")
    void stockHistoryType_description() {
        assertThat(StockHistory.StockHistoryType.DECREASE.getDescription()).isEqualTo("재고 감소");
        assertThat(StockHistory.StockHistoryType.INCREASE.getDescription()).isEqualTo("재고 증가");
    }
}
