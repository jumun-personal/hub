package com.jumunhasyeo.stock.application.dto.response;

import com.jumunhasyeo.stock.domain.entity.StockHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockHistoryResTest {

    @Test
    @DisplayName("StockHistory를 StockHistoryRes로 변환한다.")
    void from_success() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        StockHistory history = StockHistory.ofStore(hubId, productId, 8, "idem-key");

        StockHistoryRes result = StockHistoryRes.from(history);

        assertThat(result.hubId()).isEqualTo(hubId);
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.type()).isEqualTo("STORE");
        assertThat(result.quantity()).isEqualTo(8);
        assertThat(result.memo()).isNull();
    }
}
