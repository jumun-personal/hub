package com.jumunhasyeo.stock.application.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockCommandTest {

    @Test
    @DisplayName("재고 감소 명령은 허브와 상품 및 감소 수량을 보존한다")
    void decreaseStockCommand_fields() {
        // given
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // when
        DecreaseStockCommand command = new DecreaseStockCommand(hubId, productId, 10);

        // then
        assertThat(command.hubId()).isEqualTo(hubId);
        assertThat(command.productId()).isEqualTo(productId);
        assertThat(command.amount()).isEqualTo(10);
    }

    @Test
    @DisplayName("재고 증가 명령은 허브와 상품 및 증가 수량을 보존한다")
    void increaseStockCommand_fields() {
        // given
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // when
        IncreaseStockCommand command = new IncreaseStockCommand(hubId, productId, 4);

        // then
        assertThat(command.hubId()).isEqualTo(hubId);
        assertThat(command.productId()).isEqualTo(productId);
        assertThat(command.amount()).isEqualTo(4);
    }
}
