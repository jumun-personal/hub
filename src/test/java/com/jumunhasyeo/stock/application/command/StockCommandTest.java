package com.jumunhasyeo.stock.application.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockCommandTest {

    @Test
    @DisplayName("DecreaseStockCommand 필드 접근")
    void decreaseStockCommand_fields() {
        UUID productId = UUID.randomUUID();

        DecreaseStockCommand command = new DecreaseStockCommand(productId, 10);

        assertThat(command.productId()).isEqualTo(productId);
        assertThat(command.amount()).isEqualTo(10);
    }

    @Test
    @DisplayName("IncreaseStockCommand 필드 접근")
    void increaseStockCommand_fields() {
        UUID productId = UUID.randomUUID();

        IncreaseStockCommand command = new IncreaseStockCommand(productId, 4);

        assertThat(command.productId()).isEqualTo(productId);
        assertThat(command.amount()).isEqualTo(4);
    }
}
