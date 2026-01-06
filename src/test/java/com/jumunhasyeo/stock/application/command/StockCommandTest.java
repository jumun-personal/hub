package com.jumunhasyeo.stock.application.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockCommandTest {

    @Test
    @DisplayName("StoreStockCommand 필드 접근")
    void storeStockCommand_fields() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        StoreStockCommand command = new StoreStockCommand(hubId, productId, 10);

        assertThat(command.hubId()).isEqualTo(hubId);
        assertThat(command.productId()).isEqualTo(productId);
        assertThat(command.amount()).isEqualTo(10);
    }

    @Test
    @DisplayName("ShippedStockCommand 필드 접근")
    void shippedStockCommand_fields() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        ShippedStockCommand command = new ShippedStockCommand(hubId, productId, 4);

        assertThat(command.hubId()).isEqualTo(hubId);
        assertThat(command.productId()).isEqualTo(productId);
        assertThat(command.amount()).isEqualTo(4);
    }
}
